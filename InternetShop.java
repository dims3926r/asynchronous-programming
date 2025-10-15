import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * InternetShop.java
 *
 * Проста симуляція інтернет-магазину з багатопоточністю:
 * - Admin додає товари (release семафора) в робочі години.
 * - Buyers намагаються купити товар (tryAcquire семафора).
 * - Використовується Runnable + Thread, показуються Thread states.
 *
 * Зрозумілі повідомлення українською для не-програміста.
 */
public class InternetShop {

    // Клас, що представляє магазин
    static class Shop {
        // map: productName -> semaphore (кількість одиниць у наявності)
        private final Map<String, Semaphore> stock = new ConcurrentHashMap<>();
        // lock для читання/запису назв товарів
        private final Object stockLock = new Object();

        // Прапорець чи магазин відкритий
        private final AtomicBoolean shopOpen = new AtomicBoolean(false);

        // Встановити початковий товар (можна 0)
        public void addProduct(String productName, int initialQty) {
            synchronized (stockLock) {
                stock.putIfAbsent(productName, new Semaphore(0));
            }
            if (initialQty > 0) {
                restock(productName, initialQty);
            }
        }

        // Поповнення товару (викликає адміністратор)
        public void restock(String productName, int qty) {
            if (qty <= 0) return;
            Semaphore sem = stock.get(productName);
            if (sem == null) {
                synchronized (stockLock) {
                    sem = stock.computeIfAbsent(productName, k -> new Semaphore(0));
                }
            }
            sem.release(qty); // додаємо дозволи -> збільшуємо наявність
            System.out.printf("[МАГАЗИН] Адмін поповнив '%s' на %d шт. Тепер доступно (приблизно): %d\n",
                    productName, qty, sem.availablePermits());
        }

        // Спроба купити товар; якщо магазин зачинено або товару нема, повертає false
        public boolean tryBuy(String productName, long waitMillis) throws InterruptedException {
            if (!shopOpen.get()) {
                // Магазин зачинено
                return false;
            }
            Semaphore sem = stock.get(productName);
            if (sem == null) return false;

            // Спроба отримати 1 одиницю товару:
            // використаємо tryAcquire з timeout — якщо не вдалося — покупець отримає повідомлення "нема в наявності"
            boolean got = sem.tryAcquire(waitMillis, TimeUnit.MILLISECONDS);
            return got;
        }

        public int available(String productName) {
            Semaphore sem = stock.get(productName);
            return sem == null ? 0 : sem.availablePermits();
        }

        public void openShop() {
            shopOpen.set(true);
            System.out.println("[МАГАЗИН] Магазин відкрито.");
        }

        public void closeShop() {
            shopOpen.set(false);
            System.out.println("[МАГАЗИН] Магазин зачинено.");
        }

        public boolean isOpen() {
            return shopOpen.get();
        }

        public Set<String> productNames() {
            return Collections.unmodifiableSet(stock.keySet());
        }
    }

    // Admin додає товари в робочі години (Runnable)
    static class Admin implements Runnable {
        private final Shop shop;
        private final String productName;
        private final int restockQty;
        private final long restockIntervalMillis; // інтервал між поповненнями
        private final long workingTimeMillis; // скільки часу адміністратор працює в магазині (симуляція)

        public Admin(Shop shop, String productName, int restockQty, long restockIntervalMillis, long workingTimeMillis) {
            this.shop = shop;
            this.productName = productName;
            this.restockQty = restockQty;
            this.restockIntervalMillis = restockIntervalMillis;
            this.workingTimeMillis = workingTimeMillis;
        }

        @Override
        public void run() {
            Thread current = Thread.currentThread();
            System.out.printf("[АДМІН] Потік %s починає роботу.\n", current.getName());

            // Адмін працює тільки коли магазин відкритий
            long endTime = System.currentTimeMillis() + workingTimeMillis;
            try {
                while (System.currentTimeMillis() < endTime) {
                    if (!shop.isOpen()) {
                        // Чекаємо, поки магазин відкриють (штучне очікування)
                        Thread.sleep(200);
                        continue;
                    }
                    // Поповнюємо товар
                    shop.restock(productName, restockQty);

                    // Друкуємо стан потоку для демонстрації Thread.State
                    System.out.printf("[АДМІН] Поточний стан потоку %s: %s\n", current.getName(), current.getState());

                    // Чекати перед наступним поповненням
                    Thread.sleep(restockIntervalMillis);
                }
            } catch (InterruptedException e) {
                // Обробка переривання: безпечне завершення
                System.out.printf("[АДМІН] Потік %s перервано, завершення роботи.\n", current.getName());
                Thread.currentThread().interrupt();
            }
            System.out.printf("[АДМІН] Потік %s завершив роботу.\n", current.getName());
        }
    }

    // Buyer намагається купити товар (Runnable)
    static class Buyer implements Runnable {
        private final Shop shop;
        private final String buyerName;
        private final String productName;
        private final long tryIntervalMillis; // як часто пробує купити
        private final int attempts; // скільки спроб зробити
        private final long waitForStockMillis; // скільки чекати отримання товару (tryAcquire timeout)

        public Buyer(Shop shop, String buyerName, String productName, long tryIntervalMillis, int attempts, long waitForStockMillis) {
            this.shop = shop;
            this.buyerName = buyerName;
            this.productName = productName;
            this.tryIntervalMillis = tryIntervalMillis;
            this.attempts = attempts;
            this.waitForStockMillis = waitForStockMillis;
        }

        @Override
        public void run() {
            Thread current = Thread.currentThread();
            System.out.printf("[ПОКУПЕЦЬ %s] Потік %s починає покупки (ціль: '%s').\n", buyerName, current.getName(), productName);
            try {
                for (int i = 0; i < attempts; i++) {
                    // Якщо магазин зачинений — повідомляємо і пробуємо пізніше
                    if (!shop.isOpen()) {
                        System.out.printf("[ПОКУПЕЦЬ %s] Магазин зачинено. Спроба %d/%d буде повторена пізніше.\n", buyerName, i + 1, attempts);
                        Thread.sleep(tryIntervalMillis);
                        continue;
                    }

                    // Спроба купити товар
                    boolean bought = shop.tryBuy(productName, waitForStockMillis);
                    if (bought) {
                        System.out.printf("[ПОКУПЕЦЬ %s] Успіх! Ви купили '%s'. Залишок (приблизно): %d\n",
                                buyerName, productName, shop.available(productName));
                        break; // припустимо, покупець купує один раз і йде
                    } else {
                        // Не вдалось купити (або товару немає, або магазин зачинено)
                        if (!shop.isOpen()) {
                            System.out.printf("[ПОКУПЕЦЬ %s] Магазин зачинився під час спроби купівлі.\n", buyerName);
                        } else {
                            System.out.printf("[ПОКУПЕЦЬ %s] На жаль, '%s' тимчасово відсутній. Спроба %d/%d.\n", buyerName, productName, i + 1, attempts);
                        }
                        // повторимо спробу після паузи
                        Thread.sleep(tryIntervalMillis);
                    }
                }
            } catch (InterruptedException e) {
                System.out.printf("[ПОКУПЕЦЬ %s] Покупця %s перервали.\n", buyerName, current.getName());
                Thread.currentThread().interrupt();
            }
            System.out.printf("[ПОКУПЕЦЬ %s] Потік %s завершив роботу.\n", buyerName, current.getName());
        }
    }

    // --- main: приклад запуску симуляції ---
    public static void main(String[] args) {
        Shop shop = new Shop();

        // Додаємо товар в каталог (початково 0 на складі)
        shop.addProduct("Ноутбук", 0);
        shop.addProduct("Мишка", 2);

        // Створюємо адміністратора: поповнює "Ноутбук" по 1 шт кожні 4 секунди, працює 20 сек
        Admin admin = new Admin(shop, "Ноутбук", 1, 4000, 20000);
        Thread adminThread = new Thread(admin, "Admin-Thread");

        // Створюємо кількох покупців
        Buyer buyer1 = new Buyer(shop, "Олена", "Ноутбук", 3000, 5, 1500);
        Buyer buyer2 = new Buyer(shop, "Іван", "Ноутбук", 5000, 3, 1000);
        Buyer buyer3 = new Buyer(shop, "Марія", "Мишка", 2000, 2, 500);

        Thread b1 = new Thread(buyer1, "Buyer-Olena");
        Thread b2 = new Thread(buyer2, "Buyer-Ivan");
        Thread b3 = new Thread(buyer3, "Buyer-Maria");

        List<Thread> allThreads = Arrays.asList(adminThread, b1, b2, b3);

        // Запускаємо потоки (стан NEW -> RUNNABLE)
        for (Thread t : allThreads) {
            t.start();
        }

        // Симулюємо робочі години: відкриваємо магазин через 1 секунду, працює 12 секунд, потім зачиняємо
        try {
            Thread.sleep(1000);
            shop.openShop();

            // Показувати стани потоків кожні 2 секунди протягом симуляції
            long monitorEnd = System.currentTimeMillis() + 15000; // моніторимо 15 сек
            while (System.currentTimeMillis() < monitorEnd) {
                System.out.println("=== Стан потоків ===");
                for (Thread t : allThreads) {
                    System.out.printf("Потік %s — стан: %s\n", t.getName(), t.getState());
                }
                System.out.println("====================");
                Thread.sleep(2000);
            }

            // Закриваємо магазин (симуляція кінця робочого дня)
            shop.closeShop();

            // Дочекаємось завершення потоків (не більше 10 сек)
            for (Thread t : allThreads) {
                t.join(10000);
            }

            System.out.println("[СИМУЛЯЦІЯ] Завершення симуляції. Стан потоків у кінці:");
            for (Thread t : allThreads) {
                System.out.printf("%s -> %s\n", t.getName(), t.getState());
            }

        } catch (InterruptedException e) {
            System.out.println("[СИМУЛЯЦІЯ] Головний потік перервано.");
            Thread.currentThread().interrupt();
        }

        System.out.println("[СИМУЛЯЦІЯ] Всі операції завершено. Дякуємо за використання симуляції інтернет-магазину.");
    }
}
