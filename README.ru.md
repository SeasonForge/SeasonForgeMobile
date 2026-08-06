# SeasonForge Mobile

[![Download APK](https://img.shields.io/github/v/release/SeasonForge/SeasonForgeMobile?label=Download%20APK&color=3B82F6)](https://github.com/SeasonForge/SeasonForgeMobile/releases/latest)
[![Android Version](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com/)
[![License](https://img.shields.io/badge/License-Proprietary-blue.svg)](#license)

Язык: [English](README.md) | **Русский**

Мобильное Android-приложение и информативные виджеты рабочего стола для отслеживания старта и прогресса сезонов в ARPG играх (Path of Exile 1 & 2, Diablo IV, Last Epoch, Torchlight: Infinite и др.).

[Скачать последнюю версию (APK)](https://github.com/SeasonForge/SeasonForgeMobile/releases/latest)

---

## Скриншоты

<p align="center">
  <img src="docs/screenshots/screen_widgets.jpg" width="31%" alt="Home Screen Widgets" />
  <img src="docs/screenshots/screen_main.jpg" width="31%" alt="Main App Screen" />
  <img src="docs/screenshots/screen_select_widget.jpg" width="31%" alt="Select Widget Dialog" />
</p>

<p align="center">
  <img src="docs/screenshots/screen_theme_config.jpg" width="31%" alt="Widget Theme Config" />
  <img src="docs/screenshots/screen_reminders.jpg" width="31%" alt="Reminder Setup" />
</p>

---

## Возможности приложения

* **Живые виджеты рабочего стола**: 3 типа настраиваемых виджетов с обратным отсчетом времени в реальном времени. Используется нативный `Chronometer`, работающий без постоянной фоновой нагрузки и расхода батареи.
* **Отслеживание ARPG и MMO игр**: Поддержка Path of Exile 1 & 2, Diablo IV, Last Epoch, Torchlight: Infinite и других проектов.
* **Уведомления и напоминания**: Настройка напоминаний за 1 неделю, 3 дня, 24 часа, 1 час до старта или прямо в момент запуска сезона.
* **Кастомизация внешнего вида**: Настройка прозрачности фона виджетов, цветовых тем и оформления карточек игр.
* **Двуязычный интерфейс**: Полная локализация на русский и английский языки.

---

## Установка и предупреждение Google Play Protect

При установке приложения напрямую через APK-файл с GitHub операционная система или Google Play Protect могут показать предупреждение об неизвестном источнике/разработчике.

### Инструкция по установке:
1. Откройте скачанный APK-файл.
2. При появлении окна защиты нажмите **«Подробнее»** (More details).
3. Нажмите **«Всё равно установить»** (Install anyway).

---

## Технологии и стек

* **Язык программирования**: Kotlin
* **Пользовательский интерфейс**: Jetpack / Material Design 3 / RemoteViews
* **Асинхронность**: Kotlin Coroutines & Flow
* **Сетевой слой**: OkHttp / Gson
* **Архитектура**: Clean Architecture (MVVM)

---

## Сборка и разработка

### Требования:
* Android Studio Ladybug или новее
* JDK 17
* Android SDK 26+ (Android 8.0+)

### Порядок сборки:
1. Клонировать репозиторий:
   ```bash
   git clone https://github.com/SeasonForge/SeasonForgeMobile.git
   ```
2. Открыть проект в Android Studio.
3. Для сборки Debug-версии выполните:
   ```bash
   ./gradlew assembleDebug
   ```

---

## Лицензия

© 2026 SeasonForge. All rights reserved.
