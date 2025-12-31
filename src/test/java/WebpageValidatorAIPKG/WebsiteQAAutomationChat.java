package WebpageValidatorAIPKG;

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

public class WebsiteQAAutomationChat {

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

        String exactUrl = driver.getCurrentUrl();
        String title = driver.getTitle();

        String metaDescription = "MISSING";
        try {
            WebElement metaDesc = driver.findElement(By.xpath("//meta[@name='description']"));
            metaDescription = metaDesc.getAttribute("content");
        } catch (NoSuchElementException ignored) {}

        Map<String, List<HeadingElement>> headers = extractHeaders();
        List<ButtonOrLink> buttons = extractButtonsAndLinks();
        List<ParagraphStyle> paragraphs = extractParagraphs();
        List<String> grammarIssues = grammarCheck(driver.findElement(By.tagName("body")).getText());
        List<BrokenLink> brokenLinks = checkBrokenLinks();

        generateHTMLReport(exactUrl, title, metaDescription, headers, buttons, paragraphs, grammarIssues, brokenLinks);
        driver.quit();
    }

    private Map<String, List<HeadingElement>> extractHeaders() {
        Map<String, List<HeadingElement>> headers = new HashMap<>();
        for (int i = 1; i <= 6; i++) {
            String tag = "h" + i;
            List<WebElement> elements = driver.findElements(By.tagName(tag));
            List<HeadingElement> headingElements = new ArrayList<>();
            for (WebElement el : elements) {
                headingElements.add(new HeadingElement(tag, el.getText()));
            }
            headers.put(tag, headingElements);
        }
        return headers;
    }

    private List<ButtonOrLink> extractButtonsAndLinks() {
        List<ButtonOrLink> buttons = new ArrayList<>();
        for (WebElement btn : driver.findElements(By.tagName("button"))) {
            buttons.add(new ButtonOrLink("button", btn.getText(), btn.getAttribute("href")));
        }
        for (WebElement link : driver.findElements(By.tagName("a"))) {
            buttons.add(new ButtonOrLink("link", link.getText(), link.getAttribute("href")));
        }
        return buttons;
    }

    private List<ParagraphStyle> extractParagraphs() {
        List<ParagraphStyle> paragraphs = new ArrayList<>();
        for (WebElement para : driver.findElements(By.tagName("p"))) {
            paragraphs.add(new ParagraphStyle(para.getText()));
        }
        return paragraphs;
    }

    private List<String> grammarCheck(String text) throws IOException {
        JLanguageTool langTool = new JLanguageTool(new AmericanEnglish());
        List<RuleMatch> matches = langTool.check(text);
        List<String> issues = new ArrayList<>();
        for (RuleMatch match : matches) {
            issues.add("Line " + match.getLine() + ": " + match.getMessage());
        }
        return issues;
    }

    private List<BrokenLink> checkBrokenLinks() {
        List<BrokenLink> brokenLinks = new ArrayList<>();
        for (WebElement link : driver.findElements(By.tagName("a"))) {
            String linkUrl = link.getAttribute("href");
            if (linkUrl != null && !linkUrl.startsWith("javascript")) {
                try {
                    HttpURLConnection connection = (HttpURLConnection) new URL(linkUrl).openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(3000);
                    connection.setReadTimeout(3000);
                    if (connection.getResponseCode() >= 400) {
                        brokenLinks.add(new BrokenLink(linkUrl));
                    }
                } catch (Exception ignored) {}
            }
        }
        return brokenLinks;
    }

    private void generateHTMLReport(String url, String title, String metaDescription,
                                    Map<String, List<HeadingElement>> headers,
                                    List<ButtonOrLink> buttons,
                                    List<ParagraphStyle> paragraphs,
                                    List<String> grammarIssues,
                                    List<BrokenLink> brokenLinks) throws IOException {
        String reportPath = System.getProperty("user.dir") + File.separator + "WebsiteQAAutomationChat.html";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(reportPath))) {
            writer.write("<!DOCTYPE html><html><head><title>QA Automation Dashboard</title>");
            writer.write("<style>body {font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;} h1 {text-align: center; color: #333;} table {width: 100%; border-collapse: collapse; margin-top: 30px;} th, td {padding: 12px 15px; border: 1px solid #ddd; text-align: left;} th {background-color: #007bff; color: white;} tr:nth-child(even) {background-color: #f9f9f9;} tr:hover {background-color: #f1f1f1;}</style>");
            writer.write("</head><body>");
            writer.write("<h1>QA Automation Dashboard</h1>");
            writer.write("<table><thead><tr><th>URL</th><th>Title</th><th>Meta Description</th><th>Buttons/Links</th><th>H1</th><th>H2</th><th>H3</th><th>H4</th><th>H5</th><th>H6</th></tr></thead><tbody>");

            writer.write("<tr><td>" + url + "</td><td>" + title + "</td><td>" + metaDescription + "</td><td>");
            for (ButtonOrLink b : buttons) {
                writer.write("<a href='" + b.href + "'>" + b.text + "</a><br>");
            }
            writer.write("</td>");

            for (int i = 1; i <= 6; i++) {
                String tag = "h" + i;
                writer.write("<td>");
                for (HeadingElement el : headers.getOrDefault(tag, new ArrayList<>())) {
                    writer.write(el.text + "<br>");
                }
                writer.write("</td>");
            }
            writer.write("</tr></tbody></table></body></html>");
        } catch (IOException e) {
            System.err.println("Error generating HTML report: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        new WebsiteQAAutomationChat().runQAAudit("https://www.havis.com/about-havis/");
    }

    // Helper classes
    class HeadingElement {
        String tag, text;
        public HeadingElement(String tag, String text) {
            this.tag = tag;
            this.text = text;
        }
    }

    class ButtonOrLink {
        String type, text, href;
        public ButtonOrLink(String type, String text, String href) {
            this.type = type;
            this.text = text;
            this.href = href;
        }
    }

    class ParagraphStyle {
        String text;
        public ParagraphStyle(String text) {
            this.text = text;
        }
    }

    class BrokenLink {
        String url;
        public BrokenLink(String url) {
            this.url = url;
        }
    }
}