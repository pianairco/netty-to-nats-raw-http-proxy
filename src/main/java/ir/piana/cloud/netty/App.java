package ir.piana.cloud.netty;

import io.nats.client.JetStreamApiException;
import ir.piana.cloud.netty.proxy.RawHttpProxyServer;

import java.io.IOException;
import java.time.Duration;

/**
 * Hello world!
 *
 */
public class App {
    public static void main( String[] args ) throws Exception {
        System.out.println( "Hello World!" );
        JetStreamService jetStreamService = JetStreamService.create("localhost", 4222);
        subscribeOnJS(jetStreamService);
        RawHttpProxyServer.start(8080, jetStreamService, 100);
    }

    public static void subscribeOnJS(JetStreamService jetStreamService) throws JetStreamApiException, IOException {
        jetStreamService.subscribeForReply("POST.api.v11.say-hello", Duration.ofSeconds(50),
//        jetStreamService.subscribeForReply("GET.api.*", Duration.ofSeconds(50),
                fullHttpRequest -> {
                    return "Hi ".concat(fullHttpRequest.uri());
                });
    }
}
