package ru.dmitry.bookingbot;

import com.sun.net.httpserver.HttpServer;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.OutputStream;
import java.net.InetSocketAddress;

public class Main {
    public static void main(String[] args) throws Exception {
        String token = env("BOT_TOKEN");
        String username = env("BOT_USERNAME");
        long adminId = Long.parseLong(System.getenv().getOrDefault("ADMIN_ID", "0"));

        Database db = new Database("bookings.db");
        db.init();

        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(new BookingBot(token, username, adminId, db));

        System.out.println("Бот запущен: @" + username);

        // Render Web Service требует открытый порт. Локально переменной PORT нет — блок пропускается.
        String port = System.getenv("PORT");
        if (port != null && !port.isBlank()) {
            startHealthServer(Integer.parseInt(port));
        }
    }

    private static void startHealthServer(int port) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> {
            byte[] body = "Bot is alive".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.setExecutor(null);
        server.start();
        System.out.println("Health-сервер слушает порт " + port);
    }

    private static String env(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Не задана переменная окружения " + name);
        }
        return v;
    }
}
