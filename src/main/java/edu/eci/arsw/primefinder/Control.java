package edu.eci.arsw.primefinder;

import java.util.Scanner;

/**
 * Control class that manages the execution and pausing of prime finder threads.
 */
public class Control extends Thread {

    private final static int NTHREADS = 3;
    private final static int MAXVALUE = 30000000;
    private final static int TMILISECONDS = 1;

    private final int NDATA = MAXVALUE / NTHREADS;

    private final PrimeFinderThread[] pft;
    private final Object lock;

    private Control() {
        super();
        this.lock = new Object();
        this.pft = new PrimeFinderThread[NTHREADS];

        int i;
        for (i = 0; i < NTHREADS - 1; i++) {
            PrimeFinderThread elem = new PrimeFinderThread(i * NDATA, (i + 1) * NDATA, lock);
            pft[i] = elem;
        }
        pft[i] = new PrimeFinderThread(i * NDATA, MAXVALUE + 1, lock);
    }

    public static Control newControl() {
        return new Control();
    }

    private boolean areThreadsAlive() {
        for (PrimeFinderThread t : pft) {
            if (t.isAlive()) {
                return true;
            }
        }
        return false;
    }

    private void pauseAll() {
        for (PrimeFinderThread t : pft) {
            t.pauseThread();
        }
    }

    private void resumeAll() {
        synchronized (lock) {
            for (PrimeFinderThread t : pft) {
                t.resumeThread();
            }
            lock.notifyAll();
        }
    }

    private int getTotalPrimesFound() {
        int total = 0;
        for (PrimeFinderThread t : pft) {
            total += t.getPrimesCount();
        }
        return total;
    }

    @Override
    public void run() {
        // Iniciar todos los hilos trabajadores
        for (int i = 0; i < NTHREADS; i++) {
            pft[i].start();
        }

        Scanner scanner = new Scanner(System.in);

        while (areThreadsAlive()) {
            try {
                Thread.sleep(TMILISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            if (!areThreadsAlive()) {
                break;
            }

            // 1. Pausar todos los hilos trabajadores
            pauseAll();

            // 2. Mostrar la cantidad de números primos encontrados
            int primesCount = getTotalPrimesFound();
            System.out.println(">>> PAUSA: Se han encontrado " + primesCount + " números primos hasta el momento.");
            System.out.println(">>> Presione ENTER para reanudar la ejecución...");

            // 3. Esperar que el usuario presione ENTER
            scanner.nextLine();

            // 4. Reanudar la ejecución de todos los hilos usando notifyAll() sobre el
            // monitor
            resumeAll();
        }

        int finalTotal = getTotalPrimesFound();
        System.out.println(">>> EJECUCIÓN FINALIZADA.");
        System.out.println(">>> Total global de números primos encontrados: " + finalTotal);
    }
}
