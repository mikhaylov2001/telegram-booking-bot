package ru.dmitry.bookingbot;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Весь контент салона — здесь. Под нового клиента правишь только этот файл.
 */
public class Catalog {

    public static final String BUSINESS_NAME = "Барбершоп «Бритва»";
    public static final String ADDRESS = "г. Йошкар-Ола, ул. Советская, 100";
    public static final String PHONE = "+7 900 000-00-00";

    /** Услуга -> цена и длительность для текста. */
    public static final Map<String, String> SERVICES = Map.of(
            "Мужская стрижка", "900 ₽ · 45 мин",
            "Стрижка + борода", "1400 ₽ · 1 ч 15 мин",
            "Моделирование бороды", "600 ₽ · 30 мин",
            "Детская стрижка", "700 ₽ · 40 мин"
    );

    public static final List<String> SERVICE_ORDER = List.of(
            "Мужская стрижка",
            "Стрижка + борода",
            "Моделирование бороды",
            "Детская стрижка"
    );

    public static final List<String> MASTERS = List.of("Андрей", "Сергей", "Марат");

    public static final List<String> SLOTS = List.of(
            "10:00", "11:00", "12:00", "13:00",
            "15:00", "16:00", "17:00", "18:00", "19:00"
    );

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("dd.MM");

    /** Ближайшие 7 дней. */
    public static List<String> nextDays() {
        List<String> out = new ArrayList<>();
        LocalDate d = LocalDate.now();
        for (int i = 0; i < 7; i++) {
            LocalDate cur = d.plusDays(i);
            String label = cur.format(DAY_FMT) + " (" + ruWeekday(cur) + ")";
            out.add(label);
        }
        return out;
    }

    private static String ruWeekday(LocalDate d) {
        return switch (d.getDayOfWeek()) {
            case MONDAY -> "пн";
            case TUESDAY -> "вт";
            case WEDNESDAY -> "ср";
            case THURSDAY -> "чт";
            case FRIDAY -> "пт";
            case SATURDAY -> "сб";
            case SUNDAY -> "вс";
        };
    }
}
