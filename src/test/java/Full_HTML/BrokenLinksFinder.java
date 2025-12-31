package Full_HTML;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import io.github.bonigarcia.wdm.WebDriverManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.List;

public class BrokenLinksFinder {

    public static void main(String[] args) throws MalformedURLException, IOException, InterruptedException{

    	WebDriver driver;
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--remote-allow-origins=*"); // New Line
        driver = new ChromeDriver(options);
        driver.manage().window().maximize();

    		
    		// URL Checker
    		// This Java method will call URLs and retrieve their status codes.
    	
    		// Step 1: Use Selenium to gather all URLs associated with links.
    		// If the status code is greater than 400, the URL is not functioning,
    		// indicating that the link tied to the URL is broken.
  
       
        
        Thread.sleep(5000);
        String username = "demo";
        String password = "demo123"; // must be valid for your staging site

        // Target URL (remove embedded credentials)
        driver.get("https://demo:demo@123@sovereign1.wpenginepowered.com/trusted-systems/?jdhf");
        Thread.sleep(5000); // Wait for page to load

        List<WebElement> links = driver.findElements(By.tagName("a"));
        for (WebElement linkElement : links) {
            String url = linkElement.getAttribute("href");

            if (url == null || url.isEmpty()) {
                System.out.println("⚠️ Empty or missing href found");
                continue;
            }

            // Skip mailto or javascript links
            if (url.startsWith("mailto:") || url.startsWith("javascript:")) {
                System.out.println("⛔ Skipped non-HTTP URL: " + url);
                continue;
            }

            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();

                // 🔐 Add Basic Authentication Header
                String credentials = username + ":" + password;
                String encodedAuth = Base64.getEncoder().encodeToString(credentials.getBytes());
                connection.setRequestProperty("Authorization", "Basic " + encodedAuth);

                // Add User-Agent and Referer headers to simulate a real browser request
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
                connection.setRequestProperty("Referer", "https://sovereign1.wpenginepowered.com/");
                connection.setRequestProperty("Origin", "https://sovereign1.wpenginepowered.com/");
                
                connection.setRequestMethod("GET"); // Change to GET request (instead of HEAD)
                connection.setConnectTimeout(5000);  // Timeout after 5 seconds if no response
                connection.setReadTimeout(5000);     // Timeout while reading the response
                connection.setInstanceFollowRedirects(true); // Allow redirects (important for 3xx codes)

                connection.connect();

                int responseCode = connection.getResponseCode();

                if (responseCode >= 400) {
                    System.out.println("❌ Broken Link: " + url + " --> Response Code: " + responseCode);
                    // Print more details about the error response
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("Error Response: " + line);
                    }
                } else if (responseCode == HttpURLConnection.HTTP_OK) {
                    System.out.println("✅ Valid Link: " + url + " --> Response Code: " + responseCode);
                } else {
                    System.out.println("⚠️ Unexpected Response Code for: " + url + " --> Response Code: " + responseCode);
                }

                // Add a delay to avoid rate limiting issues (optional)
                Thread.sleep(1000); // Sleep for 1 second between requests

            } catch (Exception e) {
                System.out.println("❌ Exception for URL: " + url + " --> " + e.getMessage());
                // Handle connection failure, timeout, or invalid URL
            }
        }

        driver.quit();
    }
} 
        
        
        /*
        
        driver.get("https://rahulshettyacademy.com/AutomationPractice/");
    	
    		
    		//String link= driver.findElement(By.cssSelector("a[href*='soapui']")).getAttribute("href"); // Working Link
    		String link= driver.findElement(By.cssSelector("a[href*='brokenlink']")).getAttribute("href"); // Broken URL
    		HttpURLConnection connection = (HttpURLConnection) new URL(link).openConnection();	
    		connection.setRequestMethod("HEAD");
    		connection.connect();
    		int respCode= connection.getResponseCode();
    		System.out.println(respCode);}	}
*/