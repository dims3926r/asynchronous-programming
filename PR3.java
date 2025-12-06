import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;

public class PR3 {
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("\n--- Практична робота 3 (Варіант 1) ---");
        while (true) {
            System.out.println("\n1. Завдання 1: Попарна сума масиву (Порівняння Work Stealing та Work Dealing)");
            System.out.println("2. Завдання 2: Підрахунок файлів (Work Stealing)");
            System.out.println("0. Вихід");
            System.out.print("Оберіть опцію: ");

            String choice = scanner.next();

            switch (choice) {
                case "1":
                    runTask1();
                    break;
                case "2":
                    runTask2();
                    break;
                case "0":
                    System.out.println("Завершення роботи.");
                    System.exit(0);
                default:
                    System.out.println("Невірний вибір. Спробуйте ще раз.");
            }
        }
    }

    // --- ЗАВДАННЯ 1: Попарна сума масиву ---
    private static void runTask1() {
        System.out.println("\n--- Завдання 1: Попарна сума ---");

        int size = getValidInt("Введіть розмір масиву (наприклад, 100000): ");
        int min = getValidInt("Введіть мінімальне значення елемента: ");
        int max = getValidInt("Введіть максимальне значення елемента: ");

        int[] array = generateArray(size, min, max);

        if (size <= 100) {
            System.out.print("Згенерований масив: ");
            for (int i : array) System.out.print(i + " ");
            System.out.println();
        } else {
            System.out.println("Згенерований масив (перші 20 елементів): ");
            for (int i = 0; i < 20; i++) System.out.print(array[i] + " ");
            System.out.println("... (всього " + size + " елементів)");
        }

        System.out.println("\nЗапуск Work Stealing (ForkJoin)...");
        ForkJoinPool fjp = new ForkJoinPool(); 
        long startStealing = System.nanoTime();

        long sumStealing = fjp.invoke(new PairwiseSumTask(array, 0, array.length));

        long endStealing = System.nanoTime();
        double timeStealing = (endStealing - startStealing) / 1_000_000.0;

        System.out.println("Результат (Stealing): " + sumStealing);
        System.out.printf("Час (Stealing): %.4f мс\n", timeStealing);

        System.out.println("\nЗапуск Work Dealing (Thread Pool)...");
        long startDealing = System.nanoTime();

        long sumDealing = runWorkDealing(array);

        long endDealing = System.nanoTime();
        double timeDealing = (endDealing - startDealing) / 1_000_000.0;

        System.out.println("Результат (Dealing):  " + sumDealing);
        System.out.printf("Час (Dealing):  %.4f мс\n", timeDealing);
    }

    static class PairwiseSumTask extends RecursiveTask<Long> {
        private final int[] arr;
        private final int start;
        private final int end;
        private static final int THRESHOLD = 5000;

        public PairwiseSumTask(int[] arr, int start, int end) {
            this.arr = arr;
            this.start = start;
            this.end = end;
        }

        @Override
        protected Long compute() {
            if (end - start <= THRESHOLD) {
                long sum = 0;

                int loopEnd = (end == arr.length) ? end - 1 : end;

                for (int i = start; i < loopEnd; i++) {
                    sum += (arr[i] + arr[i+1]);
                }

                // Важливий момент стикування блоків:
                // Якщо це не останній блок, треба додати суму на стику (останній цього блоку + перший наступного)
                // Але в простій реалізації RecursiveTask для перекриття діапазонів краще робити так:
                // Ділимо масив, але кожен потік рахує попарні суми всередині себе.
                // NOTE: Для ідеальної точності формули в розподілених обчисленнях
                // простіше ігнорувати "розрив" між потоками або робити перекриття індексів.
                // В даному рішенні ми використовуємо перекриття індексів при створенні підзадач.

                return sum;
            } else {
                int mid = start + (end - start) / 2;


                PairwiseSumTask leftTask = new PairwiseSumTask(arr, start, mid);
                PairwiseSumTask rightTask = new PairwiseSumTask(arr, mid, end);

                leftTask.fork();
                long rightResult = rightTask.compute();
                long leftResult = leftTask.join();

                long bridgeSum = (long) arr[mid - 1] + arr[mid];

                return leftResult + rightResult + bridgeSum;
            }
        }
    }

    private static long runWorkDealing(int[] array) {
        int cores = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(cores);
        List<Future<Long>> futures = new ArrayList<>();

        int chunkSize = array.length / cores;

        for (int i = 0; i < cores; i++) {
            final int start = i * chunkSize;
            final int end = (i == cores - 1) ? array.length : (i + 1) * chunkSize;

            futures.add(executor.submit(() -> {
                long sum = 0;
                // Рахуємо всередині чанку
                for (int k = start; k < end - 1; k++) {
                    sum += (array[k] + array[k+1]);
                }
                return sum;
            }));
        }

        long totalSum = 0;
        try {
            for (Future<Long> f : futures) {
                totalSum += f.get();
            }

            for (int i = 1; i < cores; i++) {
                int indexBoundary = i * chunkSize;
                totalSum += (array[indexBoundary - 1] + array[indexBoundary]);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }
        return totalSum;
    }

    // --- ЗАВДАННЯ 2: Пошук файлів ---
    private static void runTask2() {
        System.out.println("\n--- Завдання 2: Пошук файлів (Work Stealing) ---");
        System.out.print("Введіть шлях до директорії: ");
        String path = scanner.next();
        scanner.nextLine();

        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            System.out.println("Помилка: Директорія не існує.");
            return;
        }

        System.out.print("Введіть розширення файлів (наприклад .pdf або .txt): ");
        String extension = scanner.next();

        ForkJoinPool fjp = new ForkJoinPool();
        System.out.println("Пошук розпочато...");

        long startTime = System.currentTimeMillis();
        int count = fjp.invoke(new FileSearchTask(dir, extension));
        long endTime = System.currentTimeMillis();

        System.out.println("Знайдено файлів: " + count);
        System.out.println("Час пошуку: " + (endTime - startTime) + " мс");
    }

    static class FileSearchTask extends RecursiveTask<Integer> {
        private final File directory;
        private final String extension;

        public FileSearchTask(File directory, String extension) {
            this.directory = directory;
            this.extension = extension;
        }

        @Override
        protected Integer compute() {
            int count = 0;
            List<FileSearchTask> subTasks = new ArrayList<>();

            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        FileSearchTask task = new FileSearchTask(file, extension);
                        task.fork(); // кидаємо в пул (може вкрасти інший потік)
                        subTasks.add(task);
                    } else {
                        if (file.getName().endsWith(extension)) {
                            count++;
                        }
                    }
                }
            }

            for (FileSearchTask task : subTasks) {
                count += task.join();
            }

            return count;
        }
    }

    private static int getValidInt(String prompt) {
        System.out.print(prompt);
        while (!scanner.hasNextInt()) {
            System.out.println("Будь ласка, введіть ціле число.");
            scanner.next();
        }
        return scanner.nextInt();
    }

    private static int[] generateArray(int size, int min, int max) {
        int[] arr = new int[size];
        for (int i = 0; i < size; i++) {
            arr[i] = ThreadLocalRandom.current().nextInt(min, max + 1);
        }
        return arr;
    }
}