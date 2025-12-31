package Full_HTML;


import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class BrokenLinkChecker {

	
	private static WebDriver driver;
    private static Set<String> visitedUrls = new HashSet<>();
    private static List<String> brokenLinks = new ArrayList<>();
    private static List<String> validLinks = new ArrayList<>();
    private static final int THREAD_POOL_SIZE = 10;
    private static final String BASE_URL = "https://sovereign1.wpenginepowered.com/trusted-systems/";
    private static final String USERNAME = "demo";
    private static final String PASSWORD = "demo@123";
    private static final String BASE_DOMAIN = "https://sovereign1.wpenginepowered.com";

    public static void main(String[] args) {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--remote-allow-origins=*"); // For testing only
        driver = new ChromeDriver(options);
        driver.manage().window().maximize();

        try {
            // Authenticate for the initial page load
            driver.get("https://" + USERNAME + ":" + PASSWORD + "@sovereign1.wpenginepowered.com/trusted-systems/");
            crawlWebsite(BASE_URL);
            System.out.println("\n=== Broken Links (404) ===");
            if (brokenLinks.isEmpty()) {
                System.out.println("No broken links found.");
            } else {
                brokenLinks.forEach(link -> System.out.println("404 - " + link));
            }
            System.out.println("\n=== Valid Links ===");
          //  validLinks.forEach(link -> System.out.println("200 - " + link));
        } finally {
            driver.quit();
        }
    }

    private static void crawlWebsite(String startUrl) {
        if (!visitedUrls.contains(startUrl)) {
            visitedUrls.add(startUrl);
            driver.get(startUrl);
            System.out.println("Crawling: " + startUrl);

            List<WebElement> links = driver.findElements(By.tagName("a"));
            List<String> urlsToCheck = new ArrayList<>();

            for (WebElement link : links) {
                String href = link.getAttribute("href");
                if (href != null && !href.isEmpty() && href.startsWith("http")) {
                    urlsToCheck.add(href);
                }
            }

            ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            for (String url : urlsToCheck) {
                executor.submit(() -> checkLink(url));
            }

            executor.shutdown();
            try {
                executor.awaitTermination(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                System.err.println("Executor interrupted: " + e.getMessage());
            }

            for (String url : urlsToCheck) {
                if (url.startsWith(BASE_DOMAIN) && !visitedUrls.contains(url)) {
                    crawlWebsite(url);
                }
            }
        }
    }

    private static void checkLink(String url) {
        try {
            URL linkUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) linkUrl.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            // Add Basic Authentication header if URL is within the protected domain
            if (url.startsWith(BASE_DOMAIN)) {
                String auth = USERNAME + ":" + PASSWORD;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
            }

            int statusCode = connection.getResponseCode();

            synchronized (BrokenLinkChecker.class) {
                if (statusCode == 404) {
                    brokenLinks.add(url);
                } else if (statusCode == 200) {
                    validLinks.add(url);
                } else {
                    brokenLinks.add(url + " (Status: " + statusCode + ")");
                }
            }
        } catch (Exception e) {
            synchronized (BrokenLinkChecker.class) {
                brokenLinks.add(url + " (Error: " + e.getMessage() + ")");
            }
        }
    }
}
