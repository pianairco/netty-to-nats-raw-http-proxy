package ir.piana.cloud.netty;

import io.nats.client.*;
import io.nats.client.api.PublishAck;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class JetStreamService {
    private final Connection nc;
    private final JetStream js;
    private final JetStreamManagement jsm;

    private JetStreamService(Connection nc, JetStreamManagement jsm, JetStream js) {
        this.nc = nc;
        this.jsm = jsm;
        this.js = js;
    }

    public static JetStreamService create(String host, int port) {
        try {
            Connection connection = Nats.connect(String.format("nats://%s:%s", host, port));
            JetStream jetStream = connection.jetStream();
            JetStreamManagement jetStreamManagement = connection.jetStreamManagement();
            return new JetStreamService(connection, jetStreamManagement, jetStream);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("nats not connected", e);
        }
    }

    public CompletableFuture<ByteBuf> requestForReply(byte[] request, String subject, int timeoutSeconds) {
        CompletableFuture<ByteBuf> returnedFuture = new CompletableFuture<>();
        CompletableFuture<ByteBuf> future = new CompletableFuture<>();
        Dispatcher dispatcher = nc.createDispatcher((msg) -> {
            msg.ack();
            future.complete(Unpooled.copiedBuffer(msg.getData()));
        });

        String replyInbox = NUID.nextGlobal();
        dispatcher.subscribe(replyInbox);

        Headers headers = new Headers();
        headers.add("replyTo", replyInbox);

        Message msg = NatsMessage.builder()
                .subject(subject)
                .headers(headers)
                .data(request)
                .build();

        try {
            PublishAck ack = js.publish(msg);
            System.out.println("Published request id=" + ack.getSeqno());
        } catch (Exception e) {
            returnedFuture.completeExceptionally(e);
        }

        // When reply arrives, return to HTTP client
        future.orTimeout(timeoutSeconds, TimeUnit.SECONDS).whenComplete((response, ex) -> {
            try {
                if (ex != null) {
                    returnedFuture.completeExceptionally(ex);
                } else {
                    returnedFuture.complete(response);
                }
            } finally {
                dispatcher.unsubscribe(replyInbox);
            }
        });
        return returnedFuture;
    }

    public void subscribeForReply(String subject, Duration duration, Function<FullHttpRequest, String> function) throws JetStreamApiException, IOException {
        if (jsm.getStreams(subject).isEmpty()) {
            String streamName = UUID.randomUUID().toString();
            StreamConfiguration sc = StreamConfiguration.builder()
                    .name(streamName)
                    .subjects(subject)
                    .retentionPolicy(RetentionPolicy.Interest)
                    .build();
            jsm.addStream(sc);
        }
        js.subscribe(subject, nc.createDispatcher(), msg -> {
            System.out.println("Got message");
            try {
                EmbeddedChannel channel = new EmbeddedChannel(new HttpServerCodec(), new HttpObjectAggregator(65536));
                channel.writeInbound(Unpooled.wrappedBuffer(msg.getData()));
                Object decoded = channel.readInbound();
                if (decoded instanceof FullHttpRequest req) {
                    System.out.println("Parsed HTTP request: " + req.uri());
                    // Build HTTP response
                    String body = function.apply(req);

                    FullHttpResponse response = new DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                            Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
                    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
                    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");

                    EmbeddedChannel enc = new EmbeddedChannel(new HttpResponseEncoder(), new HttpObjectAggregator(65536));
                    enc.writeOutbound(response);
                    byte[] encoded = readAll(enc);
                    String responseSubject = msg.getHeaders().getFirst("replyTo");
                    /*reply(responseSubject, encoded);*/
                    PublishAck publish = js.publish(NatsMessage.builder()
                            .subject(responseSubject)
                            .data(encoded)
                            .build());
                    System.out.println("replied");
                }
            } catch (Exception e) {
                System.out.println("Consumer failed: " + e.getMessage());
            }
            /*try {
                msg.ack();
            } catch (Exception e) {
                System.out.println("Consumer failed: " + e.getMessage());
            }*/
        }, true);
    }

    public static byte[] readAll(EmbeddedChannel channel) {
        ByteBuf buf = channel.readOutbound();
        if (buf == null) return new byte[0];

        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        buf.release(); // release buffer to prevent memory leak
        return bytes;
    }

    public void reply(String replyTo, String msg) throws JetStreamApiException, IOException {
        js.publish(NatsMessage.builder()
                .subject(replyTo)
                .data(msg.getBytes(StandardCharsets.UTF_8))
                .build());
        /*nc.publish(replyTo, msg.getBytes(StandardCharsets.UTF_8));*/
    }

    public void reply(String replyTo, byte[] msg) throws JetStreamApiException, IOException {
        js.publish(NatsMessage.builder()
                .subject(replyTo)
                .data(msg)
                .build());
        /*nc.publish(replyTo, msg.getBytes(StandardCharsets.UTF_8));*/
    }

    public void close() throws InterruptedException {
        this.nc.close();
    }
}
