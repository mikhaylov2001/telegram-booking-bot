package ru.dmitry.bookingbot;

public class Booking {
    public long id;
    public long chatId;
    public String userName;
    public String phone;
    public String service;
    public String master;
    public String day;
    public String time;

    public String pretty() {
        return "#" + id + " — " + service
                + "\nМастер: " + master
                + "\nКогда: " + day + ", " + time
                + (phone != null ? "\nТелефон: " + phone : "");
    }
}
