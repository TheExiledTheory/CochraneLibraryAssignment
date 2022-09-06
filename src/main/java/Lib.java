/*  Program to fetch review information from cochranelibrary
*   Author: Mark Cuccarese II 
*   Use: javac Lib.java && java Lib
*/
import java.util.*;
import java.io.*;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;


// Top level main function
public class Lib {
    public static void main(String[] args) {

        System.out.println("Welcome to meta-data scraper v1!");

        // Used for scraping topics 
        Topics topics = new Topics();

        try {
            // Collect topics 
            boolean b = topics.fetchTopics();
            assert(b);
        } catch (Exception e) {
            System.out.println("Error loading topics");
        }

        // Allow user to select topic
        Scanner scan = new Scanner(System.in);
        System.out.println("Please choose a provided topic: ");
        topics.chosen_topic = scan.nextLine();
        scan.close();
        
        // Verify that topic is valid 
        if (!topics.isValidTopic(topics.chosen_topic)) {
            throw new IllegalArgumentException("Invalid topic");
        } else {
            System.out.println("Valid topic: " + topics.chosen_topic);
        }

        // Clear terminal here for cleanliness! 
        System.out.print("\033\143");
        System.out.flush();

        // Used for scraping reviews 
        Reviews reviews = new Reviews(); 
 
        System.out.println("Determining number of pages to scrape...");

        try {
            // Collect the page count and setup variables for threads 
            boolean b2 = reviews.configurator(topics.chosen_topic);
            //assert(b2);
        } catch (Exception e) {
            System.out.println("Error determining review page count");
        }

        System.out.println(reviews.num_pages + " Pages fetched! Spawning threads...");
        
        try {
            // Scrape reviews for topic
            boolean b3 = reviews.runner(reviews);
            assert(b3);
            System.out.println("Worker threads fetching reviews complete!");
        } catch (Exception e) {
            System.out.println("Error loading reviews");
        }


    }
}

// Threads to fetch reviews 
class Threads extends Thread {

    // Reference to holder object 
    private Reviews reviews;

    // Constructor to store reference
    public Threads(Reviews reviews) {
        this.reviews = reviews;
    }

    // Synchronized writing to file 
    void writer(byte[] bytes) {

        try {
            // Indicate write status 
            boolean written = false; 
            do {
                try {
                    // Try to create a new file lock 
                    FileLock lock = this.reviews.out_Stream.getChannel().lock();

                    try {
                        // Try to write to file 
                        this.reviews.out_Stream.write(bytes);
                        // If success - indicate 
                        written = true; 
                    } finally {
                        // Unlock file 
                        lock.release();
                    }
                } catch (OverlappingFileLockException err) {
                    try {
                        Thread.sleep(0);
                    } catch (InterruptedException e) {
                        throw new InterruptedIOException("Interrupted while waiting to acquire file lock");
                    }
                }
            } while (!written);
        } catch (IOException e) {
            System.out.println("Failed to lock output file");
            StringWriter s = new StringWriter(); 
            e.printStackTrace(new PrintWriter(s));
            System.out.println(s.toString());
        }
    }

    // Threaded process function 
    public void run() {
        
        System.out.println("Thread worker started for page: " + this.reviews.current_page);
 
        // Initialize variables for thread
        String link = this.reviews.command_url + this.reviews.current_page;
        ProcessBuilder process = null;
        int exitCode = -1;
        Process p = null; 
        String lines = "";
        String final_lines = "";    
        BufferedReader read = null;     
        
        try { 
            
            // ./review_loader --return_reviews --url --topicname_adj --topicname_clean
            process = new ProcessBuilder("python3", this.reviews.file_path, "return_reviews", link , this.reviews.topic_name_adj, this.reviews.topic_name_clean);
            process.redirectErrorStream(true);

            // Start and wait for process 
            p = process.start();
            exitCode = p.waitFor();

            // Verify completion of script 
            if (exitCode != 0) {
                throw new Exception("Error running external python script...error code yield: " + exitCode);
            }
            //System.out.println("Exit code: " + exitCode);

            // Open the byte stream from the process
            read = new BufferedReader(new InputStreamReader(p.getInputStream()));

            // Verify BufferedReader is valid
            assert (read != null) : "Problem reading byte stream from theaded process";

            // Read the output lines 
            while (lines != null) {
                // Combine the output into a singular string 
                final_lines += (lines + "\n");
                // Read the next line 
                lines = read.readLine();
            }

            // Close the reader
            read.close();

            // Write the output string to RandomAccessFile
            try {
                // Write title block 
                System.out.println("Thread worker writing results to file...");
                Thread.sleep(1000);

                // Convert string to byte array
                byte[] temp_bytes = final_lines.getBytes();

                // Send to syncrhonization file writer
                writer(temp_bytes);

            } catch (Exception e) {
                StringWriter s = new StringWriter(); 
                e.printStackTrace(new PrintWriter(s));
                System.out.println(s.toString());
            } 
            
        } catch (Exception e) {
            System.out.println("Error in threaded processing of review_loader.py");
            StringWriter s = new StringWriter(); 
            e.printStackTrace(new PrintWriter(s));
            System.out.println(s.toString());
        }
        
        System.out.println("Thread worker finished...");

    }
}

// Class for handling reviews 
class Reviews {

    // Values provided to threads 
    String command_url = ""; 
    String topic_name_adj = "";
    String topic_name_clean = "";
    int num_pages = 0;
    volatile int current_page = 1;
    String file_path = "";
    
    FileOutputStream out_Stream = null;

    // Function to call threads 
    boolean runner(Reviews reviews) {


        /* DOES NOT DELETE PRE-EXISTING FILE */
        System.out.println("*Please note that this program will not delete pre-existing files*");

        try {
            // Create output file 
            File output_file = new File("cochrane_reviews_" + reviews.topic_name_clean + ".txt");

            // If already exists this will not do anything 
            output_file.createNewFile(); 

            // Open byte stream to file
            this.out_Stream = new FileOutputStream(output_file, true);     

        } catch (FileNotFoundException e) {
            StringWriter s = new StringWriter(); 
            e.printStackTrace(new PrintWriter(s));
            System.out.println(s.toString());
        } catch (IOException e) {
            StringWriter s = new StringWriter(); 
            e.printStackTrace(new PrintWriter(s));
            System.out.println(s.toString());
        }

        this.current_page = 0;
     
        // Create array of threads to hold references 
        Thread[] workers = new Thread[this.num_pages];

        // Thread executor loop 
        for (int i = 0; i < this.num_pages; i++) {

            // Start volatile var at 1
            this.current_page++;

            // Create a new thread and start 
            workers[i] = new Threads(reviews);
            workers[i].start();

            try {
                // Wait 1 sec before running next thread to help avoid errors
                Thread.sleep(1000);
            } catch (Exception e) {
                System.out.println("Error sleeping");
            }
        }

        // Wait for threads to finish 
        for (Thread thread : workers) {
            try {
                thread.join();
                System.out.println("Worker thread joined() to main thread...");
            } catch (InterruptedException e) {
                System.out.println("Error joining threads");
            }
        }

        try {
            // Close output file 
            this.out_Stream.close();
            System.out.println("Closed file stream to cochrane_reviews_" + reviews.topic_name_clean + ".txt");
            return true;
        } catch (IOException e) {
            System.out.println("Error closing output file");
        }

        return false;
    }

    // Function to set initials 
    boolean configurator (String topicName) throws Exception {

        // Set topic name before altering 
        this.topic_name_clean = topicName; 

        // Lazy for loop to format encoding of topicname
        try {
            topicName = topicName.replaceAll(",", "%2c"); // comma = %2c
        } catch (Exception e) {
            //System.out.println();
        }
        try {
            topicName = topicName.replaceAll(" ", "+"); // space = + 

        } catch (Exception e) {
            //System.out.println();
        }
        try {
            topicName = topicName.replaceAll("&", "%26"); // ampersand = %26
        } catch (Exception e) {
            //System.out.println();
        }

        // Format URL to be sent as a parameter to python script 
        String url = String.format("https://www.cochranelibrary.com/en/search?min_year=&max_year=&custom_min_year=&custom_max_year=&searchBy=13&searchText=%s&selectedType=review&isWordVariations=&resultPerPage=100&searchType=basic&orderBy=displayDate-true&publishDateTo=&publishDateFrom=&publishYearTo=&publishYearFrom=&displayText=%s&forceTypeSelection=true&p_p_id=scolarissearchresultsportlet_WAR_scolarissearchresults&p_p_lifecycle=0&p_p_state=normal&p_p_mode=view&p_p_col_id=column-1&p_p_col_count=1&cur=%d", topicName, topicName, this.current_page);
        
        // Get the location of file from current directory 
        String cwd = Path.of(".").toAbsolutePath().normalize().toString();
        cwd = cwd + "/review_loader.py";
        
        // Debug
        //System.out.println("Topic: " + topicName);
        //System.out.println("Current page: " + this.current_page);
        //System.out.println("Current url: " + url);
        //System.out.println("File path: " + cwd);

        // ./review_loader --return_pages --url --topicname
        ProcessBuilder process = new ProcessBuilder("python3", cwd, "return_pages", url, topicName);
        process.redirectErrorStream(true);
        // Send to process and wait 
        Process p = process.start();
        int exitCode = p.waitFor();

        //System.out.println("Exit code: " + exitCode);

        // Verify completion of script 
        if (exitCode != 0) {
            throw new Exception("Error running external python script");
        }
        
        // Grab the output data from the process
        BufferedReader read = new BufferedReader(new InputStreamReader(p.getInputStream()));

        // Python script should return a single string 
        String lines = read.readLine();
        read.close();

        // Verify return value
        assert (lines != null && lines.length() > 0) : "Error: Script completed but did not provide output :(";

        // String to float to int 
        float f = Float.parseFloat(lines);
        int inter = (int) f;

        // Value has very small chance of being X.00 so increment to next page 
        inter++;

        // Set valus that are going to be used by threads 
        this.num_pages = inter;  
        this.file_path = cwd;
        this.topic_name_adj = topicName;
        this.command_url = url.substring(0, url.length() - 1); // <- remove the page number param to be incremented by threads 

        //System.out.println("Number of pages: " + this.num_pages);

        // Verify success 
        if (this.num_pages > 0) {
            return true;
        } else {
            return false; 
        }
    }

}


// Class for handling topics 
class Topics {

    // Array of topics
    ArrayList<String> topics = new ArrayList<String>();
    String chosen_topic = "";

    // Matches topic to array list of topics
    boolean isValidTopic(String topic) {
        return topics.contains(topic);
    }

    // Fetch topics from API 
    boolean fetchTopics () throws Exception {

        System.out.println("Fetching available topic metadata...");

        // Get the path to the root folder of the project
        String cwd = Path.of(".").toAbsolutePath().normalize().toString();
        cwd = cwd + "/topic_loader.py";


        // Create a new process to execute external python script 
        ProcessBuilder process = new ProcessBuilder("python3", cwd);
        process.redirectErrorStream(true);

        // Start the process and wait for it to finish
        Process p = process.start();
        int exitCode = p.waitFor(); 

        // Check if process exited successfully
        if (exitCode != 0) {
            throw new Exception("Error running external python script");
        }
        
        // Grab the output data from the process
        BufferedReader read = new BufferedReader(new InputStreamReader(p.getInputStream()));

        // Python script should return a single string 
        String lines = read.readLine();
        assert (lines != null && lines.length() > 0);

        // Clean up output
        String adjusted_lines = null;
        adjusted_lines = lines.replace("[", "");
        adjusted_lines = adjusted_lines.replace("']", "");
        
        // Split string CAREFULLY
        String s[] = adjusted_lines.split("', "); 

        System.out.println("\n=============================");
        // Add topics to ArrayList
        for (int i = 0; i < s.length; i++) {
            s[i] = s[i].replace("'", "");
            topics.add(s[i]);
            System.out.println(s[i]);
        }
        System.out.println("=============================");

        
        System.out.println();
        return true; 
    }
} 




























