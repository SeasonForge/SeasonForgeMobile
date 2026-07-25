# ⚡ SeasonForge Mobile

[![Download Latest Release](https://img.shields.io/github/v/release/SeasonForge/SeasonForgeMobile?label=Download%20APK&color=3B82F6)](https://github.com/SeasonForge/SeasonForgeMobile/releases/latest)

Мобильное Android-приложение и информативные виджеты рабочего стола для отслеживания старта и прогресса сезонов в ARPG играх (**Path of Exile 1 & 2**, **Diablo IV**, **Last Epoch**, **Torchlight: Infinite**).

[⬇️ **Скачать последнюю версию (APK)**](https://github.com/SeasonForge/SeasonForgeMobile/releases/latest)

---

## 🚀 Возможности приложения

* **📊 3 типа виджетов на рабочий стол**:
  * **Карточка сезона** — прогресс текущей лиги и следующий запуск.
  * **Таймер (3D)** — 3 независимые плашки отсчёта (`ДНИ | ЧАСЫ | МИНУТЫ`).
  * **Гибридный (Комбинированный)** — полная сводка текущей лиги + отсчёт времени до следующей.
* **🔔 Локальные Push-уведомления**:
  * Настройка гибких напоминаний о старте сезонов (за 1 час, за 24 часа, за 3 дня, за 1 неделю, в момент старта).
* **🌐 Двуязычная локализация (RU / EN)**:
  * Переключение языка в интерфейсе и автоматическая подстройка под систему.
* **🎨 Настройка прозрачности виджетов**:
  * Индивидуальная регулировка прозрачности элементов виджета для любого фона рабочего стола.
* **⚡ Мгновенный отклик при тапе**:
  * Быстрое обновление данных и статус-индикация на виджете.

---

## 🛠 Технологии

* **Language**: Kotlin
* **UI**: Android Jetpack / Material Design 3 / RemoteViews
* **Concurrency**: Kotlin Coroutines & Flow
* **Network**: OkHttp / Gson
* **Architecture**: Clean & Modular Android Architecture

---

## ⚙️ Сборка проекта

1. Клонировать репозиторий:
   ```bash
   git clone https://github.com/SeasonForge/SeasonForgeMobile.git
   ```
2. Открыть проект в **Android Studio**.
3. Скомпилировать и запустить:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 📄 Лицензия

© 2026 SeasonForge. All rights reserved.
