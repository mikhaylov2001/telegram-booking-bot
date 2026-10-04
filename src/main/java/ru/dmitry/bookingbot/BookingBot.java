package ru.dmitry.bookingbot;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BookingBot extends TelegramLongPollingBot {

    private final String username;
    private final long adminId;
    private final Database db;

    /** Черновик записи на время диалога. */
    private final Map<Long, Booking> drafts = new ConcurrentHashMap<>();
    /** Ждём ли от пользователя телефон. */
    private final Set<Long> awaitingPhone = ConcurrentHashMap.newKeySet();

    public BookingBot(String token, String username, long adminId, Database db) {
        super(token);
        this.username = username;
        this.adminId = adminId;
        this.db = db;
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update);
            } else if (update.hasMessage()) {
                handleMessage(update);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===================== СООБЩЕНИЯ =====================

    private void handleMessage(Update update) throws Exception {
        var msg = update.getMessage();
        long chatId = msg.getChatId();

        // Пользователь прислал контакт кнопкой
        if (msg.hasContact()) {
            finishBooking(chatId, msg.getContact().getPhoneNumber(), displayName(update));
            return;
        }

        if (!msg.hasText()) return;
        String text = msg.getText().trim();

        // Ждём телефон текстом
        if (awaitingPhone.contains(chatId) && !text.startsWith("/")) {
            finishBooking(chatId, text, displayName(update));
            return;
        }

        switch (text) {
            case "/start" -> sendMenu(chatId);
            case "/book", "📅 Записаться" -> askService(chatId);
            case "/my", "📋 Мои записи" -> showMyBookings(chatId);
            case "/prices", "💈 Услуги и цены" -> sendPrices(chatId);
            case "/info", "📍 Контакты" -> sendInfo(chatId);
            case "/admin" -> sendAdminList(chatId);
            default -> send(chatId, "Не понял команду. Нажми кнопку в меню или /start");
        }
    }

    private void sendMenu(long chatId) {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        KeyboardRow r1 = new KeyboardRow();
        r1.add(new KeyboardButton("📅 Записаться"));
        r1.add(new KeyboardButton("📋 Мои записи"));
        KeyboardRow r2 = new KeyboardRow();
        r2.add(new KeyboardButton("💈 Услуги и цены"));
        r2.add(new KeyboardButton("📍 Контакты"));
        kb.setKeyboard(List.of(r1, r2));

        SendMessage m = new SendMessage();
        m.setChatId(chatId);
        m.setText("Привет! Это " + Catalog.BUSINESS_NAME + ".\n\nЗапишись онлайн за 30 секунд — выбери услугу, мастера и время.");
        m.setReplyMarkup(kb);
        exec(m);
    }

    private void sendPrices(long chatId) {
        StringBuilder sb = new StringBuilder("💈 Услуги и цены:\n\n");
        for (String s : Catalog.SERVICE_ORDER) {
            sb.append("• ").append(s).append(" — ").append(Catalog.SERVICES.get(s)).append("\n");
        }
        send(chatId, sb.toString());
    }

    private void sendInfo(long chatId) {
        send(chatId, "📍 " + Catalog.BUSINESS_NAME
                + "\nАдрес: " + Catalog.ADDRESS
                + "\nТелефон: " + Catalog.PHONE
                + "\nРаботаем: ежедневно 10:00–20:00");
    }

    // ===================== ШАГИ ЗАПИСИ =====================

    private void askService(long chatId) {
        drafts.put(chatId, new Booking());
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (String s : Catalog.SERVICE_ORDER) {
            rows.add(List.of(btn(s + " — " + Catalog.SERVICES.get(s), "svc:" + s)));
        }
        SendMessage m = new SendMessage();
        m.setChatId(chatId);
        m.setText("Шаг 1 из 4. Выбери услугу:");
        m.setReplyMarkup(new InlineKeyboardMarkup(rows));
        exec(m);
    }

    private void askMaster(Update u, long chatId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (String m : Catalog.MASTERS) {
            rows.add(List.of(btn(m, "mst:" + m)));
        }
        rows.add(List.of(btn("⬅️ Назад", "back:svc")));
        edit(u, "Шаг 2 из 4. Выбери мастера:", rows);
    }

    private void askDay(Update u, long chatId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<String> days = Catalog.nextDays();
        for (int i = 0; i < days.size(); i += 2) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(btn(days.get(i), "day:" + days.get(i)));
            if (i + 1 < days.size()) row.add(btn(days.get(i + 1), "day:" + days.get(i + 1)));
            rows.add(row);
        }
        rows.add(List.of(btn("⬅️ Назад", "back:mst")));
        edit(u, "Шаг 3 из 4. Выбери день:", rows);
    }

    private void askTime(Update u, long chatId) throws Exception {
        Booking d = drafts.get(chatId);
        List<String> busy = db.busySlots(d.master, d.day);

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (String slot : Catalog.SLOTS) {
            boolean taken = busy.contains(slot);
            row.add(btn(taken ? "✖️ " + slot : slot, taken ? "busy" : "tm:" + slot));
            if (row.size() == 3) {
                rows.add(new ArrayList<>(row));
                row.clear();
            }
        }
        if (!row.isEmpty()) rows.add(row);
        rows.add(List.of(btn("⬅️ Назад", "back:day")));

        edit(u, "Шаг 4 из 4. Свободное время у мастера " + d.master + " на " + d.day + ":", rows);
    }

    private void askPhone(long chatId) {
        awaitingPhone.add(chatId);

        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        kb.setOneTimeKeyboard(true);
        KeyboardButton share = new KeyboardButton("📱 Отправить мой номер");
        share.setRequestContact(true);
        KeyboardRow row = new KeyboardRow();
        row.add(share);
        kb.setKeyboard(List.of(row));

        SendMessage m = new SendMessage();
        m.setChatId(chatId);
        m.setText("Остался последний шаг — оставь телефон для подтверждения.\nНажми кнопку ниже или напиши номер вручную.");
        m.setReplyMarkup(kb);
        exec(m);
    }

    private void finishBooking(long chatId, String phone, String userName) throws Exception {
        Booking d = drafts.get(chatId);
        if (d == null) {
            awaitingPhone.remove(chatId);
            send(chatId, "Запись не найдена. Начни заново: /book");
            return;
        }
        d.chatId = chatId;
        d.phone = phone;
        d.userName = userName;

        // Проверка, что слот не заняли, пока клиент думал
        if (db.busySlots(d.master, d.day).contains(d.time)) {
            awaitingPhone.remove(chatId);
            drafts.remove(chatId);
            send(chatId, "Увы, это время только что заняли. Выбери другое: /book");
            return;
        }

        d.id = db.save(d);
        awaitingPhone.remove(chatId);
        drafts.remove(chatId);

        send(chatId, "✅ Готово, ты записан!\n\n" + d.pretty()
                + "\n\nАдрес: " + Catalog.ADDRESS
                + "\n\nОтменить — /my");
        sendMenu(chatId);

        if (adminId != 0) {
            send(adminId, "🔔 Новая запись\n\n" + d.pretty()
                    + "\nКлиент: " + (userName == null ? "—" : userName));
        }
    }

    private void showMyBookings(long chatId) throws Exception {
        List<Booking> list = db.activeByChat(chatId);
        if (list.isEmpty()) {
            send(chatId, "Активных записей нет. Записаться — /book");
            return;
        }
        for (Booking b : list) {
            SendMessage m = new SendMessage();
            m.setChatId(chatId);
            m.setText(b.pretty());
            m.setReplyMarkup(new InlineKeyboardMarkup(
                    List.of(List.of(btn("❌ Отменить запись", "cancel:" + b.id)))));
            exec(m);
        }
    }

    private void sendAdminList(long chatId) throws Exception {
        if (chatId != adminId) {
            send(chatId, "Команда доступна только администратору.");
            return;
        }
        List<Booking> all = db.allActive();
        if (all.isEmpty()) {
            send(chatId, "Активных записей нет.");
            return;
        }
        StringBuilder sb = new StringBuilder("📋 Все записи (" + all.size() + "):\n\n");
        for (Booking b : all) {
            sb.append(b.day).append(" ").append(b.time)
              .append(" — ").append(b.service)
              .append(" / ").append(b.master)
              .append(" / ").append(b.phone == null ? "—" : b.phone)
              .append("\n");
        }
        send(chatId, sb.toString());
    }

    // ===================== CALLBACK =====================

    private void handleCallback(Update u) throws Exception {
        String data = u.getCallbackQuery().getData();
        long chatId = u.getCallbackQuery().getMessage().getChatId();

        AnswerCallbackQuery ack = new AnswerCallbackQuery();
        ack.setCallbackQueryId(u.getCallbackQuery().getId());
        if ("busy".equals(data)) {
            ack.setText("Это время уже занято");
            ack.setShowAlert(true);
        }
        exec(ack);
        if ("busy".equals(data)) return;

        Booking d = drafts.computeIfAbsent(chatId, k -> new Booking());

        if (data.startsWith("svc:")) {
            d.service = data.substring(4);
            askMaster(u, chatId);
        } else if (data.startsWith("mst:")) {
            d.master = data.substring(4);
            askDay(u, chatId);
        } else if (data.startsWith("day:")) {
            d.day = data.substring(4);
            askTime(u, chatId);
        } else if (data.startsWith("tm:")) {
            d.time = data.substring(3);
            edit(u, "Проверь запись:\n\n" + d.service
                    + "\nМастер: " + d.master
                    + "\nКогда: " + d.day + ", " + d.time, List.of());
            askPhone(chatId);
        } else if (data.startsWith("cancel:")) {
            long id = Long.parseLong(data.substring(7));
            boolean ok = db.cancel(id, chatId);
            edit(u, ok ? "Запись #" + id + " отменена." : "Не получилось отменить.", List.of());
            if (ok && adminId != 0) send(adminId, "❌ Клиент отменил запись #" + id);
        } else if (data.startsWith("back:")) {
            switch (data.substring(5)) {
                case "svc" -> {
                    List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                    for (String s : Catalog.SERVICE_ORDER) {
                        rows.add(List.of(btn(s + " — " + Catalog.SERVICES.get(s), "svc:" + s)));
                    }
                    edit(u, "Шаг 1 из 4. Выбери услугу:", rows);
                }
                case "mst" -> askMaster(u, chatId);
                case "day" -> askDay(u, chatId);
            }
        }
    }

    // ===================== УТИЛИТЫ =====================

    private InlineKeyboardButton btn(String text, String data) {
        InlineKeyboardButton b = new InlineKeyboardButton(text);
        b.setCallbackData(data);
        return b;
    }

    private void edit(Update u, String text, List<List<InlineKeyboardButton>> rows) {
        EditMessageText e = new EditMessageText();
        e.setChatId(u.getCallbackQuery().getMessage().getChatId());
        e.setMessageId(u.getCallbackQuery().getMessage().getMessageId());
        e.setText(text);
        if (rows != null && !rows.isEmpty()) {
            e.setReplyMarkup(new InlineKeyboardMarkup(rows));
        }
        exec(e);
    }

    private void send(long chatId, String text) {
        SendMessage m = new SendMessage();
        m.setChatId(chatId);
        m.setText(text);
        exec(m);
    }

    private String displayName(Update u) {
        var from = u.getMessage().getFrom();
        String name = from.getFirstName();
        if (from.getLastName() != null) name += " " + from.getLastName();
        if (from.getUserName() != null) name += " (@" + from.getUserName() + ")";
        return name;
    }

    private void exec(Object method) {
        try {
            if (method instanceof SendMessage m) execute(m);
            else if (method instanceof EditMessageText m) execute(m);
            else if (method instanceof AnswerCallbackQuery m) execute(m);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
