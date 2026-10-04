# Бот онлайн-записи (Telegram, Java 17)

Готовый бот записи для салона, барбершопа, автосервиса, медцентра.
Собирается под нового клиента правкой одного файла `Catalog.java`.

## Что умеет

1. Запись в 4 шага: услуга → мастер → день → время.
2. Занятые слоты помечены и не кликаются — двойной записи не будет.
3. Сбор телефона кнопкой «Отправить мой номер» или текстом.
4. «Мои записи» с кнопкой отмены.
5. Уведомление администратору о каждой новой записи и отмене.
6. Команда `/admin` — список всех активных записей.
7. Хранение в SQLite, файл `bookings.db` создаётся сам.

## Запуск

1. Получи токен у @BotFather в Telegram.
2. Узнай свой числовой ID у @userinfobot — это ADMIN_ID.
3. Собери и запусти:

```bash
mvn clean package

export BOT_TOKEN="токен_от_BotFather"
export BOT_USERNAME="имя_бота_без_собаки"
export ADMIN_ID="твой_числовой_id"

java -jar target/booking-bot.jar
```

## Под нового клиента

Правишь только `Catalog.java`:

- `BUSINESS_NAME`, `ADDRESS`, `PHONE` — реквизиты
- `SERVICES` + `SERVICE_ORDER` — услуги и цены
- `MASTERS` — мастера
- `SLOTS` — часы работы

Всё остальное трогать не нужно. Переделка под нового заказчика — 15 минут.

## Деплой на сервер

Самый дешёвый вариант — VPS за 200–300 ₽/мес (Timeweb, Beget, Reg.ru).

```bash
# на сервере
sudo apt install openjdk-17-jre maven -y
# закинуть jar, затем systemd-сервис:
```

`/etc/systemd/system/booking-bot.service`:

```ini
[Unit]
Description=Booking Bot
After=network.target

[Service]
WorkingDirectory=/opt/booking-bot
Environment=BOT_TOKEN=xxx
Environment=BOT_USERNAME=xxx
Environment=ADMIN_ID=xxx
ExecStart=/usr/bin/java -jar /opt/booking-bot/booking-bot.jar
Restart=always

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable --now booking-bot
```

## Сколько брать за такой бот

- Как есть, с заменой контента: 10 000 – 15 000 ₽
- Плюс настройка на сервере клиента: +5 000 ₽
- Плюс напоминания за час до визита: +5 000 ₽
- Плюс онлайн-оплата: +10 000 ₽
# telegram-booking-bot
