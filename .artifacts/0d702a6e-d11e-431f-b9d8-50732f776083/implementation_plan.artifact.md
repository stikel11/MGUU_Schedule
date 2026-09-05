# Основа приложения расписания (MD3)

Создание фундамента для приложения расписания университета на основе предоставленного скриншота с использованием Material Design 3.

## Предложенные изменения

### Данные

#### [NEW] [Lesson.kt](file:///C:/Users/pavs2/AndroidStudioProjects/MGUUSchedule2/app/src/main/java/com/mguuschedule/model/Lesson.kt)
Модель данных для учебного занятия.

### Навигация

#### [NEW] [Screen.kt](file:///C:/Users/pavs2/AndroidStudioProjects/MGUUSchedule2/app/src/main/java/com/mguuschedule/ui/navigation/Screen.kt)
Определение маршрутов навигации.

### Экраны и Компоненты

#### [NEW] [WeekCalendar.kt](file:///C:/Users/pavs2/AndroidStudioProjects/MGUUSchedule2/app/src/main/java/com/mguuschedule/ui/components/WeekCalendar.kt)
Компонент горизонтального календаря на неделю.

#### [NEW] [ScheduleScreen.kt](file:///C:/Users/pavs2/AndroidStudioProjects/MGUUSchedule2/app/src/main/java/com/mguuschedule/ui/screens/ScheduleScreen.kt)
Главный экран с расписанием.

#### [NEW] [ProfileScreen.kt](file:///C:/Users/pavs2/AndroidStudioProjects/MGUUSchedule2/app/src/main/java/com/mguuschedule/ui/screens/ProfileScreen.kt)
Экран профиля (заглушка).

### Основная активность

#### [MODIFY] [MainActivity.kt](file:///C:/Users/pavs2/AndroidStudioProjects/MGUUSchedule2/app/src/main/java/com/mguuschedule/MainActivity.kt)
Настройка Scaffold с BottomNavigation и NavHost.

## План верификации

### Ручная проверка
- Запуск приложения на эмуляторе/устройстве.
- Проверка переключения дней в календаре.
- Проверка навигации между вкладками "Расписание" и "Профиль".
