package com.adobs.logscope.core;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class LogManager {

    private static final String TAG = "LogManager";
    
    // SAFETY 1: Bounded Queue. 
    // अगर डिस्क बहुत धीमी है, तो यह 50,000 लॉग्स तक मेमोरी में रखेगा। उसके बाद ड्रॉप कर देगा ताकि OOM न हो।
    private static final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>(50000);
    
    private static volatile boolean isRunning = false;
    private static Thread writerThread;
    private static BufferedWriter bufferedWriter;

    /**
     * फाइल और बैकग्राउंड थ्रेड तैयार करना
     */
    public static void init(String packageName) {
        if (isRunning) return; // Prevent multiple initializations

        try {
            File root = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "LogScope");
            File appFolder = new File(root, packageName);
            
            if (!appFolder.exists() && !appFolder.mkdirs()) {
                Log.e(TAG, "Critical Error: Failed to create directories at " + appFolder.getAbsolutePath());
                return;
            }

            String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
            File currentLogFile = new File(appFolder, "Log_" + timeStamp + ".txt");
            
            // SAFETY 2: Buffered I/O. यह हार्डवेयर पर सीधा दबाव नहीं डालता।
            bufferedWriter = new BufferedWriter(new FileWriter(currentLogFile, true), 8192); // 8KB Buffer
            
            isRunning = true;
            startWriterThread();

            write("--- Session Started: " + packageName + " ---");

        } catch (IOException e) {
            Log.e(TAG, "Error creating log file: ", e);
        }
    }

    /**
     * यह बैकग्राउंड थ्रेड चुपचाप Queue से लॉग्स निकालकर फाइल में लिखता है
     * (Optimized: Removed excessive flush() to prevent I/O blocking)
     */
    private static void startWriterThread() {
        writerThread = new Thread(() -> {
            int logCount = 0; // बैच (Batch) ट्रैकिंग के लिए
            
            while (isRunning || !logQueue.isEmpty()) {
                try {
                    // यह तब तक ब्लॉक रहता है जब तक Queue में कोई नया लॉग न आ जाए (Zero CPU Waste)
                    String message = logQueue.take(); 
                    
                    if (bufferedWriter != null) {
                        bufferedWriter.write(message);
                        bufferedWriter.newLine();
                        
                        // PERFORMANCE FIX: हर लॉग पर flush() करने के बजाय, सिर्फ हर 100 लॉग्स के बाद flush करें।
                        logCount++;
                        if (logCount % 100 == 0) {
                            bufferedWriter.flush();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    // VISIBILITY FIX: Full Stack Trace Logging
                    Log.e(TAG, "Disk Write Failed with Exception: ", e);
                }
            }
            closeSilently();
        }, "LogScope-AsyncWriter");
        
        // Target App की परफॉरमेंस को प्रभावित न करे इसलिए न्यूनतम प्राथमिकता
        writerThread.setPriority(Thread.MIN_PRIORITY); 
        writerThread.start();
    }

    /**
     * FAST API: यह मेथड LogHook द्वारा कॉल किया जाएगा।
     * यह सिर्फ माइक्रोसेकंड्स लेता है क्योंकि यह सीधे डिस्क पर नहीं लिखता।
     */
    public static void write(String message) {
        if (!isRunning) return;

        String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        String formattedLog = time + " : " + message;
        
        // Non-blocking Insert. 
        // अगर Queue भर जाती है, तो .offer() false रिटर्न करेगा और हम ऐप क्रैश होने के बजाय लॉग ड्रॉप कर देंगे।
        boolean isAdded = logQueue.offer(formattedLog);
        if (!isAdded) {
            Log.e(TAG, "Log Queue Full! Dropping logs to prevent Target App OOM (Out of Memory).");
        }
    }

    /**
     * ग्रेसफुल शटडाउन (जब ऐप बंद हो)
     */
    public static void shutdown() {
        isRunning = false;
        if (writerThread != null) {
            writerThread.interrupt();
        }
    }

    private static void closeSilently() {
        if (bufferedWriter != null) {
            try {
                // शटडाउन होने पर बचा हुआ सारा डेटा डिस्क पर सुरक्षित कर दें
                bufferedWriter.flush();
                bufferedWriter.close();
            } catch (IOException ignored) {}
        }
    }
}

