package Full_HTML;

import org.languagetool.JLanguageTool;
import org.languagetool.language.AmericanEnglish;
import org.languagetool.rules.RuleMatch;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import io.github.bonigarcia.wdm.WebDriverManager;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.NoSuchElementException;

public class DynamicDashboard {
    private WebDriver driver;
    private WebDriverWait wait;

    public void runQAAudit(String url) throws Exception {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--remote-allow-origins=*");
        driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        driver.get(url);

        String pageTitle = driver.getTitle();
        String metaDescription = "";

        try {
            metaDescription = driver.findElement(By.xpath("//meta[@name='description']")).getAttribute("content");
        } catch (NoSuchElementException e) {
            metaDescription = "MISSING";
        }

        Map<String, List<Map<String, String>>> headingsData = new LinkedHashMap<>();

        for (int i = 1; i <= 6; i++) {
            String tag = "h" + i;
            List<WebElement> elements = driver.findElements(By.tagName(tag));
            List<Map<String, String>> tagData = new ArrayList<>();

            for (WebElement el : elements) {
                Map<String, String> data = new HashMap<>();
                data.put("Tag Details", el.getText());
                data.put("Font Family", el.getCssValue("font-family"));
                data.put("Font Size", el.getCssValue("font-size"));
                data.put("Font Weight", el.getCssValue("font-weight"));
                data.put("Line Height", el.getCssValue("line-height"));
                data.put("Letter Spacing", el.getCssValue("letter-spacing"));
                data.put("Hex Color", el.getCssValue("color"));
                tagData.add(data);
            }
            headingsData.put(tag, tagData);
        }

        List<Map<String, String>> pTagsData = new ArrayList<>();
        for (WebElement p : driver.findElements(By.tagName("p"))) {
            Map<String, String> pData = new HashMap<>();
            pData.put("Tag Details", p.getText());
            pData.put("Font Family", p.getCssValue("font-family"));
            pData.put("Font Size", p.getCssValue("font-size"));
            pData.put("Font Weight", p.getCssValue("font-weight"));
            pData.put("Line Height", p.getCssValue("line-height"));
            pData.put("Letter Spacing", p.getCssValue("letter-spacing"));
            pData.put("Hex Color", p.getCssValue("color"));
            pTagsData.add(pData);
        }

        generateHTML(pageTitle, metaDescription, headingsData, pTagsData);
        driver.quit();
    }

    private void generateHTML(String title, String metaDescription, Map<String, List<Map<String, String>>> headingsData, List<Map<String, String>> pTagsData) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter("DynamicDashboard.html"));
        writer.write("<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'><title>Dashboard Report</title><link href='https://cdnjs.cloudflare.com/ajax/libs/tailwindcss/2.2.19/tailwind.min.css' rel='stylesheet'></head><body class='bg-gray-100 p-8'><div class='container mx-auto bg-white p-6 rounded-lg shadow-lg'>");
        writer.write("<h2 class='text-xl font-semibold text-center mb-4'>URL and SEO Information</h2><table class='w-full border border-gray-300'><thead class='bg-gray-200'><tr><th class='p-2'>URL</th><th class='p-2'>Meta Description</th></tr></thead><tbody><tr><td class='p-2'>" + title + "</td><td class='p-2'>" + metaDescription + "</td></tr></tbody></table>");

        for (String tag : headingsData.keySet()) {
            writer.write("<h3 class='text-lg font-semibold text-center mb-4'>" + tag.toUpperCase() + " Tags</h3><table class='w-full border border-gray-300'><thead class='bg-gray-200'><tr><th class='p-2'>Tag Details</th><th class='p-2'>Font Family</th><th class='p-2'>Font Size</th><th class='p-2'>Font Weight</th><th class='p-2'>Line Height</th><th class='p-2'>Letter Spacing</th><th class='p-2'>Hex Color</th></tr></thead><tbody>");
            for (Map<String, String> data : headingsData.get(tag)) {
                writer.write("<tr>");
                for (String value : data.values()) {
                    writer.write("<td class='p-2'>" + value + "</td>");
                }
                writer.write("</tr>");
            }
            writer.write("</tbody></table>");
        }

        writer.write("<h3 class='text-lg font-semibold text-center mb-4'>P Tags</h3><table class='w-full border border-gray-300'><thead class='bg-gray-200'><tr><th class='p-2'>Tag Details</th><th class='p-2'>Font Family</th><th class='p-2'>Font Size</th><th class='p-2'>Font Weight</th><th class='p-2'>Line Height</th><th class='p-2'>Letter Spacing</th><th class='p-2'>Hex Color</th></tr></thead><tbody>");
        for (Map<String, String> pData : pTagsData) {
            writer.write("<tr>");
            for (String value : pData.values()) {
                writer.write("<td class='p-2'>" + value + "</td>");
            }
            writer.write("</tr>");
        }
        writer.write("</tbody></table></div></body></html>");
        writer.close();
    }

    public static void main(String[] args) throws Exception {
        DynamicDashboard dashboard = new DynamicDashboard();
        dashboard.runQAAudit("https://www.havis.com/about-havis/");
    }
}
