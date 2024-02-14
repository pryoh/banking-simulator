/* Name: Ryan Monahan
Course: CNT 4714 Spring 2024
Assignment title: Project 2 – Synchronized, Cooperating Threads Under Locking
Due Date: February 11, 2024
*/

package app;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

public class Main {
    public static void main(String[] args) {

        try {
            PrintStream dualPrintStream = new DualPrintStream("output.txt", System.out);
            System.setOut(dualPrintStream);
            System.setErr(dualPrintStream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }

        BankAccount account = new BankAccount();
        ExecutorService executorService = Executors.newFixedThreadPool(17);

        System.out.println("*** SIMULATION BEGINS ***");
        System.out.printf("%-30s %-50s %-50s %s\n", "Deposit Agents", "Withdrawal Agents", "Balance", "Transaction Number");
        System.out.printf("%-30s %-50s %-50s %s\n", "--------------", "-----------------", "-------", "------------------");

        for (int i = 0; i < 10; i++) {
            executorService.submit(new Withdrawal(account, "Agent WT" + (i+1)));
        }

        executorService.submit(new Auditor(account, "INTERNAL BANK"));
        executorService.submit(new Auditor(account, "TREASURY DEPT"));

        // Create and start threads
        for (int i = 0; i < 5; i++) {
            executorService.submit(new Depositor(account, "Agent DT" + (i+1)));
        }

        // Simulate for some time (manually stop the program)
        try {
            Thread.sleep(100000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        executorService.shutdownNow();
    }
}

class DualPrintStream extends PrintStream {
    private final PrintStream consoleStream;

    public DualPrintStream(String filePath, PrintStream consoleStream) throws FileNotFoundException {
        // Open "output.txt" in overwrite mode by not passing 'true' for append to FileOutputStream
        super(new FileOutputStream(filePath, false)); // false or omitting the second parameter will overwrite the file
        this.consoleStream = consoleStream;
    }

    @Override
    public void write(byte[] buf, int off, int len) {
        try {
            super.write(buf, off, len); // Write to file
            super.flush(); // Ensure data is written to file
            consoleStream.write(buf, off, len); // Write to console
            consoleStream.flush(); // Ensure data is output to console
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        super.close();
        // Do not close consoleStream to avoid closing System.out
    }
}

class BankAccount {
    private int balance;
    private int transactionCount = 0;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition sufficientFunds = lock.newCondition();
    private static final String TRANSACTION_LOG_FILE = "transactions.csv";

    public BankAccount() {
        this.balance = 0;
        initializeLogFile();
    }

    private void initializeLogFile() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(TRANSACTION_LOG_FILE, false))) { // false to overwrite each time
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deposit(int amount, String name) {
        lock.lock();
        try {
            balance += amount;
            transactionCount++;

            String transactionMessage = String.format("%-81s %-60s %-41d", 
                                          name + " deposits $" + amount, 
                                          "(+) Balance is $" + balance, 
                                          transactionCount);
            System.out.println(transactionMessage);

            if (amount > 350) {
                String flaggedTransactionMessage = String.format("\n\n*** Flagged Transaction - Depositor %s Made A Deposit In Excess Of $350.00 USD - See Flagged Transaction Log.\n\n", name);
                System.out.println(flaggedTransactionMessage);
                logTransaction(transactionCount, "deposit", amount, name, true); // Log only if the deposit is over $350
            }

            sufficientFunds.signalAll();

        } finally {
            lock.unlock();
        }
    }
    
    public boolean withdraw(int amount, String name) {
    lock.lock();
    try {
        boolean success = balance >= amount;
        String transactionMessage;
        if (success) {
            balance -= amount;
            transactionMessage = String.format("%-30s %-50s %-60s %-5d", 
                                               "", 
                                               name + " withdraws $" + amount, 
                                               "(-) Balance is $" + String.format("%d", balance), 
                                               ++transactionCount);
        } else {
            transactionMessage = String.format("%-30s %-50s %-60s", 
                                               "", 
                                               name + " withdraws $" + amount, 
                                               "(xxxxxx) WITHDRAWAL BLOCKED - INSUFFICIENT FUNDS!!!");

        }
        System.out.println(transactionMessage);
        if (success && amount > 75) {
            transactionMessage = String.format("\n\n*** Flagged Transaction - Withdrawal %s Made A Withdrawal In Excess Of $75.00 USD - See Flagged Transaction Log.\n\n", name);
            System.out.println(transactionMessage);
            logTransaction(transactionCount, "withdraw", amount, name, true); // true for flagged transaction
        }
        sufficientFunds.signalAll();
        return success;
        } finally {
            lock.unlock();
        }
    }

    private void logTransaction(int transactionNumber, String type, int amount, String name, boolean isFlagged) {
        String dateTimeString = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        
        // Conditionally prepend a tab for deposit transactions
        String prefix = type.equals("deposit") ? "\t" : "";
        
        String logEntry = String.format("%s%s %s issued %s of $%d at %s EST Transaction Number : %d\n",
                                        prefix, // Prepend tab for deposit transactions
                                        type.equals("deposit") ? "Depositor Agent" : "Withdrawal Agent",
                                        name,
                                        type,
                                        amount,
                                        dateTimeString,
                                        transactionNumber);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(TRANSACTION_LOG_FILE, true))) {
            bw.write(logEntry);
        } catch (IOException e) {
            System.err.println("An error occurred while logging transaction to file: " + e.getMessage());
        }
    }
    
    public int getBalance() {
        lock.lock();
        try {
            return balance;
        } finally {
            lock.unlock();
        }
    }

    public int getTransactionCount() {
        lock.lock();
        try {
            return transactionCount;
        } finally {
            lock.unlock();
        }
    }
}


class Depositor implements Runnable {
    private final BankAccount account;
    private final Random random = new Random();
    private final String name;

    public Depositor(BankAccount account, String name) {
        this.account = account;
        this.name = name;
    }

    @Override
    public void run() {
        while (true) {
            int amount = 1 + random.nextInt(500); // Generates an amount in cents
            account.deposit(amount, name); // Pass name to deposit
            try {
                TimeUnit.MILLISECONDS.sleep(random.nextInt(15000)); // Sleep between 500 to 1000 ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}



class Withdrawal implements Runnable {
    private final BankAccount account;
    private final Random random = new Random();
    private final String name;

    public Withdrawal(BankAccount account, String name) {
        this.account = account;
        this.name = name;
    }

    @Override
    public void run() {
        while (true) {
            int amount = 1 + random.nextInt(100);
            try {
                account.withdraw(amount, name);
                TimeUnit.MILLISECONDS.sleep(random.nextInt(3000));
            } catch (InterruptedException e) {
                System.out.println(name + " interrupted.");
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}

class Auditor implements Runnable {
    private final BankAccount account;
    private int lastAuditTransactionCount = 0;
    private final String name;
    private final Random random = new Random();

    public Auditor(BankAccount account, String name) {
        this.account = account;
        this.name = name;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Introduce a more significant random component to the audit interval for variation
                int auditInterval = 18000 + random.nextInt(1000) - 500;
                TimeUnit.MILLISECONDS.sleep(auditInterval);

                // Perform the audit
                performAudit();

            } catch (InterruptedException e) {
                System.out.println(name + " AUDITOR interrupted.");
                Thread.currentThread().interrupt(); // Preserve interruption status
            }
        }
    }

    private void performAudit() {
        double balance = account.getBalance();
        int currentTransactionCount = account.getTransactionCount();
        int transactionsSinceLastAudit = currentTransactionCount - lastAuditTransactionCount;

        System.out.println("\n\n*************************************************************************************************************************\n\n" +
                           " - " + name + " AUDITOR FINDS CURRENT ACCOUNT BALANCE TO BE: $" + String.format("%.2f", balance) +
                           ". Number of transactions since last audit: " + transactionsSinceLastAudit + "\n\n*************************************************************************************************************************\n\n");

        lastAuditTransactionCount = currentTransactionCount;
    }
}