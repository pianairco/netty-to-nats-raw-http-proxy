package ir.piana.cloud.netty.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import ir.piana.cloud.netty.JetStreamService;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RawHttpProxyHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private final JetStreamService jetStreamService;
    private final ExecutorService natsExecutor;

    public RawHttpProxyHandler(JetStreamService jetStreamService) {
        this.jetStreamService = jetStreamService;
        // Executor for blocking NATS requests
        natsExecutor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors() * 2,
                r -> {
                    Thread t = new Thread(r);
                    t.setName("nats-request-thread-" + t.getId());
                    t.setDaemon(true);
                    return t;
                }
        );
    }

    private final List<ByteBuf> chunks = new ArrayList<>();
    private boolean headersParsed = false;
    private int contentLength = 0;
    private int headerEndIndex = -1;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf in) {
        // Retain chunk for later processing
        ByteBuf retained = in.retainedDuplicate();
        chunks.add(retained);

        if (!headersParsed) {
            // Scan accumulated chunks for \r\n\r\n
            headerEndIndex = findHeaderEnd(chunks);
            if (headerEndIndex != -1) {
                // Headers complete
                headersParsed = true;
                // Optionally, parse Content-Length for POST/PUT
                contentLength = parseContentLength(chunks, headerEndIndex);
            }
        }

        if (headersParsed) {
            int totalReadable = totalReadableBytes(chunks);
            if (totalReadable >= headerEndIndex + contentLength) {
                // We have full request: headers + body
                CompositeByteBuf fullRequest = Unpooled.compositeBuffer();
                for (ByteBuf buf : chunks) {
                    fullRequest.addComponent(true, buf);
                }

                // fullRequest now contains: <request-line + headers + \r\n\r\n + body>
                System.out.println("Full HTTP request bytes: " + fullRequest.readableBytes());

                byte[] bytes = new byte[fullRequest.readableBytes()];
                fullRequest.getBytes(fullRequest.readerIndex(), bytes);

                int wildcardIndex = indexOfWildcard(bytes);
                int newLineIndex = wildcardIndex < 0 ? indexOfNewLine(bytes) : 0;
                String methodAndPath = new String(Arrays.copyOfRange(bytes, 0, wildcardIndex > 0 ? wildcardIndex : (newLineIndex - 9)), StandardCharsets.UTF_8);
                String subject = methodAndPath.replace(" ", "").replace("/", ".");

                ctx.executor().execute(() -> {
                    try {
                        jetStreamService.requestForReply(bytes, subject, 5)
                                .whenComplete((resp, err) -> {
                                    if (err != null) {
                                        ctx.writeAndFlush("HTTP/1.1 500 Internal Server Error\r\n\r\n");
                                    } else {
                                        try {
                                            ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
                                        } catch (Exception e) {
                                            //ToDo
                                            e.printStackTrace();
                                        }
                                    }
                                });

                    } catch (Exception e) {
                        ctx.writeAndFlush(Unpooled.wrappedBuffer(("Error: " + e.getMessage()).getBytes()))
                                .addListener(ChannelFutureListener.CLOSE);
                    }
                });

                // Forward fullRequest or process as needed
                // Example: forwardToNats(fullRequest);

                // Reset state for next request
                chunks.clear();
                headersParsed = false;
                contentLength = 0;
                headerEndIndex = -1;
            }
        }
    }

    private int findHeaderEnd(List<ByteBuf> chunks) {
        int index = 0;
        int state = 0; // track \r\n\r\n sequence
        for (ByteBuf buf : chunks) {
            for (int i = buf.readerIndex(); i < buf.writerIndex(); i++) {
                byte b = buf.getByte(i);
                switch (state) {
                    case 0: if (b == '\r') state = 1; break;
                    case 1: if (b == '\n') state = 2; else state = 0; break;
                    case 2: if (b == '\r') state = 3; else state = 0; break;
                    case 3: if (b == '\n') return index; else state = 0; break;
                }
                index++;
            }
        }
        return -1; // not found yet
    }

    private int totalReadableBytes(List<ByteBuf> chunks) {
        int total = 0;
        for (ByteBuf buf : chunks) {
            total += buf.readableBytes();
        }
        return total;
    }

    private int parseContentLength(List<ByteBuf> chunks, int headerEndIndex) {
        // Optional: parse Content-Length from headers
        byte[] headerBytes = new byte[headerEndIndex + 4];
        int offset = 0;
        for (ByteBuf buf : chunks) {
            int toRead = Math.min(buf.readableBytes(), headerBytes.length - offset);
            buf.getBytes(buf.readerIndex(), headerBytes, offset, toRead);
            offset += toRead;
            if (offset >= headerBytes.length) break;
        }
        String headersStr = new String(headerBytes, StandardCharsets.UTF_8);
        for (String line : headersStr.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) {
                return Integer.parseInt(line.split(":", 2)[1].trim());
            }
        }
        return 0;
    }

    public static int indexOfNewLine(byte[] data) {
        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\n') {
                return i; // index of first '\n'
            }
        }
        return -1; // not found
    }

    public static int indexOfWildcard(byte[] data) {
        for (int i = 0; i < data.length; i++) {
            if (data[i] == '?') {
                return i; // index of first '\n'
            }
        }
        return -1; // not found
    }
}
