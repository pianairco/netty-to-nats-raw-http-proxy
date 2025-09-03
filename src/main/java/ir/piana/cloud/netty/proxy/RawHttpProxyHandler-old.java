/*
package ir.piana.cloud.netty.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import ir.piana.cloud.netty.JetStreamService;

import java.lang.ref.ReferenceQueue;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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


    */
/*ByteBuf requestBuffer;
    private StringBuilder requestBuilder = new StringBuilder();
    private boolean headersParsed = false;
    private int partOneHttpReqLen = 0;
    private int contentLength = 0;

    List<String> bodyContainedMethods = Arrays.asList("POST", "PUT");

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf in) {
        byte[] rawHttpRequest = null;
        ByteBuf temp = in.copy();

        int idx = -1;
        if (!headersParsed) {
            String chunk = in.toString(StandardCharsets.UTF_8);
            requestBuilder.append(chunk);
            in.release();
            idx = requestBuilder.indexOf("\r\n\r\n");
            if (idx != -1) {
                if (requestBuffer == null) {
                    requestBuffer = temp.copy();
                } else {
                    requestBuffer = Unpooled.copiedBuffer(requestBuffer, temp.slice(0, idx + 4));
                }
                String method = requestBuilder.substring(8, 11);
                if (bodyContainedMethods.contains(method)) {
                    // Headers end at idx
                    String headersText = requestBuilder.substring(0, idx);
                    String[] lines = headersText.split("\r\n");

                    Map<String, String> headersMap = new HashMap<>();
                    for (int i = 1; i < lines.length; i++) {
                        String[] kv = lines[i].split(":", 2);
                        if (kv.length == 2) headersMap.put(kv[0].trim(), kv[1].trim());
                    }

                    if (headersMap.containsKey("Content-Length")) {
                        contentLength = Integer.parseInt(headersMap.get("Content-Length"));
                    }
                    partOneHttpReqLen = idx + 4;
                    headersParsed = true;
                }
            } else {
                if (requestBuffer == null) {
                    requestBuffer = temp.copy();
                } else {
                    requestBuffer = Unpooled.copiedBuffer(requestBuffer, temp);
                }
            }
        }

        if (headersParsed) {
            if (contentLength <= 0) {
                rawHttpRequest = requestBuffer.array();
                requestBuffer = temp.readRetainedSlice(partOneHttpReqLen);
                requestBuilder = new StringBuilder(requestBuffer.toString(StandardCharsets.UTF_8));
                headersParsed = false;
                partOneHttpReqLen = 0;
                handleRawHttpRequest(ctx, rawHttpRequest);
                return;
            } else if (idx > 0) {
                requestBuffer = Unpooled.copiedBuffer(requestBuffer, temp.readRetainedSlice(partOneHttpReqLen));
            }
        }
        String body = null;
        boolean requestReady = false;
        if (headersParsed && requestBuilder.length() >= partOneHttpReqLen + contentLength) {
            body = requestBuilder.substring(partOneHttpReqLen, contentLength);
            requestReady = true;
            requestBuilder.setLength(0);
            headersParsed = false;
            contentLength = 0;
        }
        String rawRequest = new String(rawBytes, StandardCharsets.UTF_8);
        // Extract path from request line (naive)
        String firstLine = rawRequest.split("\r\n", 2)[0];
        String[] s = firstLine.split(" ");
        String s1 = s[1].split("\\?")[0].replaceAll("/", ".");
        String subject = s[0].concat(".").concat(s1.startsWith(".") ? s1.substring(1) : s1);
        ctx.executor().execute(() -> {
            try {
                jetStreamService.requestForReply(rawRequest, subject, 5)
                        .whenComplete((resp, err) -> {
                            if (err != null) {
                                ctx.writeAndFlush("HTTP/1.1 500 Internal Server Error\r\n\r\n");
                            } else {
                                *//*
*/
/*String body = "Hello from Netty raw server!\nYou sent:\n\n" + rawRequest;
                                String response =
                                        "HTTP/1.1 200 OK\r\n" +
                                                "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                                                "Content-Type: text/plain\r\n" +
                                                "Connection: close\r\n" +
                                                "\r\n" +
                                                body;

                                ByteBuf out = Unpooled.copiedBuffer(response, StandardCharsets.UTF_8);
                                ctx.writeAndFlush(out).addListener(ChannelFutureListener.CLOSE);*//*
*/
/*
                                ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);

                                *//*
*/
/*ctx.writeAndFlush(Unpooled.wrappedBuffer(resp.getBytes(StandardCharsets.UTF_8)));*//*
*/
/*
                            }
                        });

            } catch (Exception e) {
                ctx.writeAndFlush(Unpooled.wrappedBuffer(("Error: " + e.getMessage()).getBytes()))
                        .addListener(ChannelFutureListener.CLOSE);
            }
        });
    }*//*


    private final StringBuilder headerBuffer = new StringBuilder();
    private ByteBuf bodyBuffer = null;
    private boolean headersParsed = false;
    private int contentLength = 0;
    private String requestLine = null;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf in) {
        String chunk = in.toString(StandardCharsets.UTF_8);
        in.release(); // release incoming buffer

        if (!headersParsed) {
            headerBuffer.append(chunk);
            int idx = headerBuffer.indexOf("\r\n\r\n");
            if (idx != -1) {
                // Headers end here
                String headersText = headerBuffer.substring(0, idx);
                String[] lines = headersText.split("\r\n");

                // Request line
                requestLine = lines[0];
                System.out.println("Request line: " + requestLine);

                // Parse headers into map
                Map<String, String> headersMap = new HashMap<>();
                for (int i = 1; i < lines.length; i++) {
                    String[] kv = lines[i].split(":", 2);
                    if (kv.length == 2) headersMap.put(kv[0].trim(), kv[1].trim());
                }

                // Optionally print Content-Type
                String method = requestLine.split(" ")[0];
                if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                    String contentType = headersMap.getOrDefault("Content-Type", "unknown");
                    System.out.println("Content-Type: " + contentType);

                    if (headersMap.containsKey("Content-Length")) {
                        contentLength = Integer.parseInt(headersMap.get("Content-Length"));
                        bodyBuffer = Unpooled.buffer(contentLength);
                    }
                }

                headersParsed = true;

                // Remove header part from buffer
                String remaining = headerBuffer.substring(idx + 4);
                if (!remaining.isEmpty()) {
                    bodyBuffer.writeBytes(remaining.getBytes(StandardCharsets.UTF_8));
                }
                headerBuffer.setLength(0); // clear header buffer
            }
        } else if (bodyBuffer != null) {
            bodyBuffer.writeBytes(chunk.getBytes(StandardCharsets.UTF_8));
        }

        // Check if full body is received
        if (headersParsed && (bodyBuffer == null || bodyBuffer.readableBytes() >= contentLength)) {
            // Combine request line + headers + body into a single ByteBuf
            StringBuilder fullHeader = new StringBuilder();
            fullHeader.append(requestLine); // optional, or keep original request line
            // You can reconstruct full headers if needed
            ByteBuf headerBytes = Unpooled.copiedBuffer(fullHeader.toString(), StandardCharsets.UTF_8);

            ByteBuf fullRequest;
            if (bodyBuffer != null) {
                fullRequest = Unpooled.wrappedBuffer(headerBytes, bodyBuffer);
            } else {
                fullRequest = headerBytes;
            }

            // fullRequest now contains headers + body
            System.out.println("Full request bytes length: " + fullRequest.readableBytes());

            byte[] bytes = new byte[fullRequest.readableBytes()];
            fullRequest.getBytes(fullRequest.readerIndex(), bytes);

            int i = indexOfNewLine(bytes);
            String methodAndPath = new String(Arrays.copyOfRange(bytes, 0, i - 9), StandardCharsets.UTF_8);
            String subject = methodAndPath.replace(" ", ".").replace("/", ".");

            ctx.executor().execute(() -> {
                try {
                    jetStreamService.requestForReply(fullRequest.array(), subject, 5)
                            .whenComplete((resp, err) -> {
                                if (err != null) {
                                    ctx.writeAndFlush("HTTP/1.1 500 Internal Server Error\r\n\r\n");
                                } else {
                                    ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
                                }
                            });

                } catch (Exception e) {
                    ctx.writeAndFlush(Unpooled.wrappedBuffer(("Error: " + e.getMessage()).getBytes()))
                            .addListener(ChannelFutureListener.CLOSE);
                }
            });

            // Reset for next request
            headersParsed = false;
            contentLength = 0;
            bodyBuffer = null;
            requestLine = null;

            // You can forward fullRequest to NATS or elsewhere here
        }
    }

    public static int indexOfDoubleCRLF(byte[] data) {
        for (int i = 0; i < data.length - 3; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n' &&
                    data[i + 2] == '\r' && data[i + 3] == '\n') {
                return i; // start index of \r\n\r\n
            }
        }
        return -1; // not found
    }

    public static int indexOfNewLine(byte[] data) {
        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\n') {
                return i; // index of first '\n'
            }
        }
        return -1; // not found
    }

    void handleRawHttpRequest(ChannelHandlerContext ctx, byte[] raw) {

    }
    */
/*@Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        byte[] rawBytes = new byte[msg.readableBytes()];
        msg.readBytes(rawBytes);

        String rawRequest = new String(rawBytes, StandardCharsets.UTF_8);
        // Extract path from request line (naive)
        String firstLine = rawRequest.split("\r\n", 2)[0];
        String[] s = firstLine.split(" ");
        String s1 = s[1].split("\\?")[0].replaceAll("/", ".");
        String subject = s[0].concat(".").concat(s1.startsWith(".") ? s1.substring(1) : s1);
        ctx.executor().execute(() -> {
            try {
                jetStreamService.requestForReply(rawRequest, subject, 5)
                        .whenComplete((resp, err) -> {
                            if (err != null) {
                                ctx.writeAndFlush("HTTP/1.1 500 Internal Server Error\r\n\r\n");
                            } else {
                                *//*
*/
/*String body = "Hello from Netty raw server!\nYou sent:\n\n" + rawRequest;
                                String response =
                                        "HTTP/1.1 200 OK\r\n" +
                                                "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                                                "Content-Type: text/plain\r\n" +
                                                "Connection: close\r\n" +
                                                "\r\n" +
                                                body;

                                ByteBuf out = Unpooled.copiedBuffer(response, StandardCharsets.UTF_8);
                                ctx.writeAndFlush(out).addListener(ChannelFutureListener.CLOSE);*//*
*/
/*
                                ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);

                                *//*
*/
/*ctx.writeAndFlush(Unpooled.wrappedBuffer(resp.getBytes(StandardCharsets.UTF_8)));*//*
*/
/*
                            }
                        });

            } catch (Exception e) {
                ctx.writeAndFlush(Unpooled.wrappedBuffer(("Error: " + e.getMessage()).getBytes()))
                        .addListener(ChannelFutureListener.CLOSE);
            }
        });
    }*//*

}
*/
