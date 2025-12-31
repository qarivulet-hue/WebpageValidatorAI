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
import java.util.concurrent.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class WebsiteQA2HTML {
    private WebDriver driver;
    private WebDriverWait wait;
    private List<WebsiteData> websiteDataList = new ArrayList<>();
    private static final int LINK_CHECK_THREADS = 10;

    public static void muteLogs() {
        LogManager.getLogManager().reset();
        Logger globalLogger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        globalLogger.setLevel(Level.WARNING);

        Logger seleniumLogger = Logger.getLogger("org.openqa.selenium");
        seleniumLogger.setLevel(Level.WARNING);

        Logger nettyLogger = Logger.getLogger("io.netty");
        nettyLogger.setLevel(Level.WARNING);

        Logger asyncHttpLogger = Logger.getLogger("org.asynchttpclient");
        asyncHttpLogger.setLevel(Level.WARNING);

        for (Handler h : Logger.getLogger("").getHandlers()) {
            if (h instanceof ConsoleHandler) {
                h.setLevel(Level.WARNING);
            }
        }
    }

    public void runQAAudit(String url) throws Exception {
        if (driver == null) {
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--remote-allow-origins=*");
            options.addArguments("--headless=new");
            driver = new ChromeDriver(options);
            driver.manage().window().setSize(new Dimension(1920, 1200));
            wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        }

        driver.get(url);

        String exactUrl = driver.getCurrentUrl();
        String title = driver.getTitle();

        String metaDescription = "";
        try {
            WebElement metaDesc = driver.findElement(By.xpath("//meta[@name='description']"));
            metaDescription = metaDesc.getAttribute("content");
        } catch (NoSuchElementException ex) {
            metaDescription = "MISSING";
        }

        Map<String, List<HeadingElement>> headers = new HashMap<>();
        for (int i = 1; i <= 6; i++) {
            String tag = "h" + i;
            List<WebElement> elements = driver.findElements(By.tagName(tag));
            List<HeadingElement> headingElements = new ArrayList<>();
            for (WebElement el : elements) {
                headingElements.add(extractHeadingStyle(el, tag));
            }
            headers.put(tag, headingElements);
        }

        List<ButtonOrLink> buttons = new ArrayList<>();
        
        // Enhanced button extraction - multiple selectors
        List<WebElement> buttonElements = new ArrayList<>();
        buttonElements.addAll(driver.findElements(By.tagName("button")));
        buttonElements.addAll(driver.findElements(By.cssSelector("input[type='button']")));
        buttonElements.addAll(driver.findElements(By.cssSelector("input[type='submit']")));
        buttonElements.addAll(driver.findElements(By.cssSelector("[role='button']")));
        
        for (WebElement btn : buttonElements) {
            buttons.add(extractButtonOrLink(btn, "button"));
        }
        
        for (WebElement link : driver.findElements(By.tagName("a"))) {
            buttons.add(extractButtonOrLink(link, "a"));
        }

        List<ParagraphStyle> paragraphs = new ArrayList<>();
        for (WebElement para : driver.findElements(By.tagName("p"))) {
            paragraphs.add(extractParagraphStyle(para));
        }

        String visibleText = driver.findElement(By.tagName("body")).getText();
        List<String> textChunks = Arrays.asList(visibleText.split("\\n+"));
        List<GrammarIssue> grammarIssues = parallelGrammarCheck(textChunks);

        List<BrokenLink> brokenLinks = parallelBrokenLinkCheck(driver.findElements(By.tagName("a")));

        WebsiteData websiteData = new WebsiteData(
                exactUrl, title, metaDescription,
                headers, buttons, paragraphs,
                grammarIssues, brokenLinks
        );
        websiteDataList.add(websiteData);

        generateDashboardHTML();
    }

    private List<GrammarIssue> parallelGrammarCheck(List<String> textChunks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<List<GrammarIssue>>> futures = new ArrayList<>();
        for (String chunk : textChunks) {
            if (chunk.trim().isEmpty()) continue;
            futures.add(executor.submit(() -> grammarCheck(chunk)));
        }
        List<GrammarIssue> allIssues = new ArrayList<>();
        for (Future<List<GrammarIssue>> f : futures) {
            try {
                allIssues.addAll(f.get());
            } catch (Exception ignored) {}
        }
        executor.shutdown();
        return allIssues;
    }

    private List<BrokenLink> parallelBrokenLinkCheck(List<WebElement> links) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(LINK_CHECK_THREADS);
        List<Future<BrokenLink>> futures = new ArrayList<>();
        for (WebElement link : links) {
            String linkUrl = link.getAttribute("href");
            String linkText = extractTextFromElement(link);
            if (linkUrl != null && linkUrl.startsWith("http")) {
                futures.add(executor.submit(() -> {
                    int status = getHttpStatus(linkUrl);
                    if (status >= 400) {
                        return new BrokenLink(linkText, linkUrl, String.valueOf(status));
                    }
                    return null;
                }));
            }
        }
        List<BrokenLink> brokenLinks = new ArrayList<>();
        for (Future<BrokenLink> f : futures) {
            try {
                BrokenLink bl = f.get();
                if (bl != null) brokenLinks.add(bl);
            } catch (Exception ignored) {}
        }
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
        return brokenLinks;
    }

    private HeadingElement extractHeadingStyle(WebElement el, String tag) {
        String text = extractTextFromElement(el);
        text = text.trim().replaceAll("\\s+", " ");

        return new HeadingElement(
                tag,
                text,
                el.getCssValue("font-family"),
                el.getCssValue("font-size"),
                el.getCssValue("font-weight"),
                el.getCssValue("line-height"),
                el.getCssValue("letter-spacing"),
                el.getCssValue("color")
        );
    }

    private ButtonOrLink extractButtonOrLink(WebElement el, String type) {
        String buttonText = extractTextFromElement(el);
        
        return new ButtonOrLink(
                type,
                buttonText,
                el.getCssValue("background-color"),
                el.getCssValue("font-size"),
                el.getCssValue("font-weight"),
                el.getCssValue("padding"),
                el.getCssValue("border-radius"),
                el.getAttribute("href"),
                el.getCssValue("color")
        );
    }

    private String extractTextFromElement(WebElement el) {
        try {
            String tagName = el.getTagName().toLowerCase();
            if ("img".equals(tagName)) {
                return "";
            }
            
            String text = "";
            text = el.getText();
            if (text != null && !text.trim().isEmpty()) {
                return cleanText(text.trim());
            }
            
            text = el.getAttribute("textContent");
            if (text != null && !text.trim().isEmpty()) {
                return cleanText(text.trim());
            }
            
            text = el.getAttribute("innerText");
            if (text != null && !text.trim().isEmpty()) {
                return cleanText(text.trim());
            }
            
            if ("input".equals(tagName) || "textarea".equals(tagName)) {
                text = el.getAttribute("value");
                if (text != null && !text.trim().isEmpty()) {
                    return cleanText(text.trim());
                }
            }
            
            text = el.getAttribute("aria-label");
            if (text != null && !text.trim().isEmpty()) {
                return cleanText("[aria-label: " + text.trim() + "]");
            }
            
            text = el.getAttribute("title");
            if (text != null && !text.trim().isEmpty()) {
                return cleanText("[title: " + text.trim() + "]");
            }
            
            if ("button".equals(tagName)) {
                String className = el.getAttribute("class");
                String id = el.getAttribute("id");
                
                if (className != null && className.contains("close")) {
                    return "[Close Button]";
                }
                if (className != null && className.contains("menu")) {
                    return "[Menu Button]";
                }
                if (className != null && className.contains("submit")) {
                    return "[Submit Button]";
                }
                
                return "[Button]";
            }
            
            try {
                JavascriptExecutor js = (JavascriptExecutor) driver;
                Object result = js.executeScript(
                    "var elem = arguments[0];" +
                    "var text = elem.textContent || elem.innerText || '';" +
                    "return text.replace(/\\s+/g, ' ').trim();", el);
                if (result != null && !result.toString().trim().isEmpty()) {
                    return cleanText(result.toString().trim());
                }
            } catch (Exception ignored) {}
            
            return "";
            
        } catch (Exception e) {
            return "";
        }
    }

    private String cleanText(String text) {
        if (text == null) return "";
        
        text = text.replaceAll("<[^>]+>", "");
        text = text.replaceAll("\\s+", " ").trim();
        text = text.replaceAll("&[a-zA-Z]+;", "");
        
        return text;
    }

    private ParagraphStyle extractParagraphStyle(WebElement el) {
        String text = extractTextFromElement(el);
        
        return new ParagraphStyle(
                text,
                el.getCssValue("font-family"),
                el.getCssValue("font-size"),
                el.getCssValue("font-weight"),
                el.getCssValue("line-height"),
                el.getCssValue("letter-spacing"),
                el.getCssValue("color")
        );
    }

    private List<GrammarIssue> grammarCheck(String text) throws Exception {
        List<GrammarIssue> issues = new ArrayList<>();
        try {
            JLanguageTool langTool = new JLanguageTool(new AmericanEnglish());
            List<RuleMatch> matches = langTool.check(text);
            for (RuleMatch match : matches) {
                String context = text.substring(match.getFromPos(), match.getToPos());
                String suggestion = match.getSuggestedReplacements().isEmpty() ? "" :
                        String.join(", ", match.getSuggestedReplacements());
                issues.add(new GrammarIssue(context, suggestion, match.getMessage()));
            }
        } catch (Throwable e) {
            // ignore grammar errors
        }
        return issues;
    }

    private int getHttpStatus(String urlStr) {
        int retries = 3;
        while (retries > 0) {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setInstanceFollowRedirects(true);
                return connection.getResponseCode();
            } catch (Exception e) {
                retries--;
                if (retries == 0) {
                    return 400;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return 400;
    }

    private void generateDashboardHTML() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n");
        sb.append("  <title>SEO Dashboard Layout</title>\n");
        sb.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("  <script src=\"https://cdn.tailwindcss.com\"></script>\n");
        sb.append("</head>\n<body class=\"bg-gray-50 py-10\">\n<div class=\"max-w-7xl mx-auto px-6\">\n");

        for (WebsiteData data : websiteDataList) {
            // URL and SEO
            sb.append("<section class=\"mb-12\">\n");
            sb.append("<h2 class=\"text-2xl font-bold text-center mb-4\">URL and SEO Information</h2>\n");
            sb.append("<div class=\"overflow-x-auto rounded-lg shadow bg-white\">\n");
            sb.append("<table class=\"min-w-full text-sm divide-y divide-gray-200\">\n");
            sb.append("<thead><tr class=\"bg-gray-100 border-b\">\n");
            sb.append("<th class=\"px-4 py-3 font-semibold text-left\">URL</th>\n");
            sb.append("<th class=\"px-4 py-3 font-semibold text-left\">SEO Page Title</th>\n");
            sb.append("<th class=\"px-4 py-3 font-semibold text-left\">Meta Description</th>\n");
            sb.append("</tr></thead>\n<tbody>\n");
            sb.append("<tr class=\"border-b hover:bg-gray-50\">\n");
            sb.append("<td class=\"px-4 py-2 text-blue-600 underline hover:text-blue-800 cursor-pointer break-all\">")
              .append(escapeHtml(data.url)).append("</td>\n");
            sb.append("<td class=\"px-4 py-2 break-words\">").append(escapeHtml(data.title)).append("</td>\n");
            sb.append("<td class=\"px-4 py-2 break-words\">").append(escapeHtml(data.metaDescription)).append("</td>\n");
            sb.append("</tr>\n");
            sb.append("</tbody></table></div></section>\n");

            // Headings H1-H6
            sb.append("<section class=\"mb-12\">\n<h2 class=\"text-2xl font-bold text-center mb-4\">H1 to H6 Tags Details</h2>\n<div class=\"space-y-8\">\n");
            for (int i = 1; i <= 6; i++) {
                String tag = "h"+i;
                sb.append("<div><h3 class=\"text-lg font-semibold text-center mb-2\">")
                  .append(tag.toUpperCase()).append(" Tag Details</h3>\n");
                sb.append("<div class=\"overflow-x-auto rounded-md shadow bg-white\">\n");
                sb.append("<table class=\"min-w-full text-sm\"><thead><tr class=\"bg-gray-100 border-b\">\n");
                sb.append("<th class=\"px-3 py-2 text-center\">Tag Details</th>\n");
                sb.append("<th class=\"px-3 py-2 text-center\">Font Family</th>\n");
                sb.append("<th class=\"px-3 py-2 text-center\">Font Size</th>\n");
                sb.append("<th class=\"px-3 py-2 text-center\">Font Weight</th>\n");
                sb.append("<th class=\"px-3 py-2 text-center\">Line Height</th>\n");
                sb.append("<th class=\"px-3 py-2 text-center\">Letter Spacing</th>\n");
                sb.append("<th class=\"px-3 py-2 text-center\">Hex Color</th></tr></thead><tbody>\n");
                if (data.headers.containsKey(tag)) {
                    for (HeadingElement h : data.headers.get(tag)) {
                        String headingText = h.text == null ? "" : h.text;
                        String hexColor = cssColorToHex(h.color);
                        sb.append("<tr><td class=\"px-3 py-2\">").append(escapeHtml(headingText)).append("</td>")
                          .append("<td class=\"px-3 py-2\">").append(escapeHtml(h.fontFamily)).append("</td>")
                          .append("<td class=\"px-3 py-2\">").append(escapeHtml(h.fontSize)).append("</td>")
                          .append("<td class=\"px-3 py-2\">").append(escapeHtml(h.fontWeight)).append("</td>")
                          .append("<td class=\"px-3 py-2\">").append(escapeHtml(h.lineHeight)).append("</td>")
                          .append("<td class=\"px-3 py-2\">").append(escapeHtml(h.letterSpacing)).append("</td>")
                          .append("<td class=\"px-3 py-2 flex items-center justify-center space-x-2\">")
                          .append("<span>").append(escapeHtml(hexColor)).append("</span>")
                          .append("<div class=\"w-5 h-5 inline-block border border-gray-300\" style=\"background-color: ").append(hexColor).append(";\"></div>")
                          .append("</td></tr>\n");
                    }
                }
                sb.append("</tbody></table></div></div>\n");
            }
            sb.append("</div></section>\n");

            // Paragraphs
            sb.append("<section class=\"mb-12 pt-12\">\n<h2 class=\"text-2xl font-bold text-center mb-4\">P Tags Section</h2>\n");
            sb.append("<div class=\"overflow-x-auto rounded-md shadow bg-white\">\n<table class=\"min-w-full text-sm\"><thead><tr class=\"bg-gray-100 border-b\">\n");
            sb.append("<th class=\"px-3 py-2 text-center\">Tag Details</th>\n");
            sb.append("<th class=\"px-3 py-2 text-center\">Font Family</th>\n");
            sb.append("<th class=\"px-3 py-2 text-center\">Font Size</th>\n");
            sb.append("<th class=\"px-3 py-2 text-center\">Font Weight</th>\n");
            sb.append("<th class=\"px-3 py-2 text-center\">Line Height</th>\n");
            sb.append("<th class=\"px-3 py-2 text-center\">Letter Spacing</th>\n");
            sb.append("<th class=\"px-3 py-2 text-center\">Hex Color</th></tr></thead><tbody>\n");
            for (ParagraphStyle p : data.paragraphs) {
                String hexColor = cssColorToHex(p.color);
                sb.append("<tr><td class=\"px-3 py-2\">").append(escapeHtml(p.text)).append("</td>")
                  .append("<td class=\"px-3 py-2\">").append(escapeHtml(p.fontFamily)).append("</td>")
                  .append("<td class=\"px-3 py-2\">").append(escapeHtml(p.fontSize)).append("</td>")
                  .append("<td class=\"px-3 py-2\">").append(escapeHtml(p.fontWeight)).append("</td>")
                  .append("<td class=\"px-3 py-2\">").append(escapeHtml(p.lineHeight)).append("</td>")
                  .append("<td class=\"px-3 py-2\">").append(escapeHtml(p.letterSpacing)).append("</td>")
                  .append("<td class=\"px-3 py-2 flex items-center justify-center space-x-2\">")
                  .append("<span>").append(escapeHtml(hexColor)).append("</span>")
                  .append("<div class=\"w-5 h-5 inline-block border border-gray-300\" style=\"background-color: ").append(hexColor).append(";\"></div>")
                  .append("</td></tr>\n");
            }
            sb.append("</tbody></table></div></section>\n");

            // Buttons only
            sb.append("<section class=\"mb-12\">\n<h2 class=\"text-2xl font-bold text-center mb-4\">Buttons Section</h2>\n");
            sb.append("<div class=\"overflow-x-auto rounded-md shadow bg-white\">\n<table class=\"min-w-full text-sm\"><thead><tr class=\"bg-gray-100 border-b\">\n");
            sb.append("<th class=\"px-3 py-2 text-center\">Button Text</th>\n");
            sb.append("<th class=\"px-3 py-2 text-center\">Button Color</th>\n");
            sb.append("<th class=\"px-3 py-2 text-center\">Font Size</th>\n");
            sb.append("<th class=\"px-3 py-2 text-center\">Font Weight</th>\n");
            sb.append("<th class=\"px-3 py-2 text-center\">Padding</th>\n");
            sb.append("<th class=\"px-3 py-2 text-center\">Border Radius</th></tr></thead><tbody>\n");
            for (ButtonOrLink b : data.buttons) {
                if ("button".equalsIgnoreCase(b.type)) {
                    String hexColor = cssColorToHex(b.buttonColor);
                    sb.append("<tr><td class=\"px-3 py-2\">").append(escapeHtml(b.text)).append("</td>")
                      .append("<td class=\"px-3 py-2 flex items-center justify-center space-x-2\">")
                      .append("<span>").append(escapeHtml(hexColor)).append("</span>")
                      .append("<div class=\"w-5 h-5 inline-block border border-gray-300\" style=\"background-color: ").append(hexColor).append(";\"></div>")
                      .append("</td>")
                      .append("<td class=\"px-3 py-2\">").append(escapeHtml(b.fontSize)).append("</td>")
                      .append("<td class=\"px-3 py-2\">").append(escapeHtml(b.fontWeight)).append("</td>")
                      .append("<td class=\"px-3 py-2\">").append(escapeHtml(b.padding)).append("</td>")
                      .append("<td class=\"px-3 py-2\">").append(escapeHtml(b.borderRadius)).append("</td></tr>\n");
                }
            }
            sb.append("</tbody></table></div></section>\n");

            // All buttons and links
            sb.append("<section class=\"mb-12\">\n<h2 class=\"text-2xl font-bold text-center mb-4\">All Buttons and Link Text Details</h2>\n");
            sb.append("<div class=\"overflow-x-auto rounded-md shadow bg-white\">\n<table class=\"min-w-full text-sm\"><thead><tr class=\"bg-gray-100 border-b\">\n");
            sb.append("<th class=\"px-3 py-2 text-left\">Button/Link Text</th>\n");
            sb.append("<th class=\"px-3 py-2 text-left\">URL</th>\n");
            sb.append("<th class=\"px-3 py-2 text-left\">Font Size</th>\n");
            sb.append("<th class=\"px-3 py-2 text-left\">Font Weight</th>\n");
            sb.append("<th class=\"px-3 py-2 text-left\">Line Height</th>\n");
            sb.append("<th class=\"px-3 py-2 text-left\">Letter Spacing</th>\n");
            sb.append("<th class=\"px-3 py-2 text-left\">Padding</th>\n");
            sb.append("<th class=\"px-3 py-2 text-left\">Border Radius</th>\n");
            sb.append("<th class=\"px-3 py-2 text-left\">Background Color</th>\n");
            sb.append("<th class=\"px-3 py-2 text-left\">Text Color</th>\n");
            sb.append("</tr></thead><tbody>\n");
            for (ButtonOrLink b : data.buttons) {
                String url_ = b.href != null ? b.href : "#";
                String bgHexColor = cssColorToHex(b.buttonColor);
                String textHexColor = cssColorToHex(b.textColor);
                sb.append("<tr>")
                  .append("<td class=\"px-3 py-2 underline text-blue-600 hover:text-blue-800 cursor-pointer\">")
                  .append(escapeHtml(b.text)).append("</td>")
                  .append("<td class=\"px-3 py-2 text-blue-600 underline hover:text-blue-800 cursor-pointer\">")
                  .append(escapeHtml(url_)).append("</td>")
                  .append("<td class=\"px-3 py-2\">").append(escapeHtml(b.fontSize)).append("</td>")
                  .append("<td class=\"px-3 py-2\">").append(escapeHtml(b.fontWeight)).append("</td>")
                 // .append("<td class=\"px-3 py-2\">").append(escapeHtml(b.lineHeight)).append("</td>")
                 // .append("<td class=\"px-3 py-2\">").append(escapeHtml(b.letterSpacing)).append("</td>")
                  .append("<td class=\"px-3 py-2\">").append(escapeHtml(b.padding)).append("</td>")
                  .append("<td class=\"px-3 py-2\">").append(escapeHtml(b.borderRadius)).append("</td>")
                  .append("<td class=\"px-3 py-2 flex items-center space-x-2\">")
                  .append("<span>").append(escapeHtml(bgHexColor)).append("</span>")
                  .append("<div class=\"w-5 h-5 inline-block border border-gray-300\" style=\"background-color: ").append(bgHexColor).append(";\"></div>")
                  .append("</td>")
                  .append("<td class=\"px-3 py-2 flex items-center space-x-2\">")
                  .append("<span>").append(escapeHtml(textHexColor)).append("</span>")
                  .append("<div class=\"w-5 h-5 inline-block border border-gray-300\" style=\"background-color: ").append(textHexColor).append(";\"></div>")
                  .append("</td></tr>\n");
            }
            sb.append("</tbody></table></div></section>\n");

            // Spelling and Grammar
            sb.append("<section class=\"mb-12\">\n<h2 class=\"text-2xl font-bold text-center mb-4\">Spelling and Grammar</h2>\n");
            sb.append("<div class=\"overflow-x-auto rounded-md shadow bg-white\">\n<table class=\"min-w-full text-sm\"><thead><tr class=\"bg-gray-100 border-b\">\n");
            sb.append("<th class=\"px-3 py-2 text-left\">Content</th>\n");
            sb.append("<th class=\"px-3 py-2 text-left\">Suggested Correction</th></tr></thead><tbody>\n");
            for (GrammarIssue gi : data.grammarIssues) {
                sb.append("<tr><td class=\"px-3 py-2 text-left\">").append(escapeHtml(gi.context)).append("</td>")
                  .append("<td class=\"px-3 py-2 text-left\">").append(escapeHtml(gi.suggestion)).append("</td></tr>\n");
            }
            sb.append("</tbody></table></div></section>\n");

            // Broken links
            sb.append("<section>\n<h2 class=\"text-2xl font-bold text-center mb-4\">Broken Links</h2>\n");
            sb.append("<div class=\"overflow-x-auto rounded-md shadow bg-white\">\n<table class=\"min-w-full text-sm\"><thead><tr class=\"bg-gray-100 border-b\">\n");
            sb.append("<th class=\"px-3 py-2 text-left\">Link Text</th>\n");
            sb.append("<th class=\"px-3 py-2 text-left\">URL</th>\n");
            sb.append("<th class=\"px-3 py-2 text-left\">Status</th></tr></thead><tbody>\n");

            for (BrokenLink bl : data.brokenLinks) {
                sb.append("<tr><td class=\"px-3 py-2 text-left\">").append(escapeHtml(bl.text)).append("</td>")
                  .append("<td class=\"px-3 py-2 text-blue-600 underline hover:text-blue-800 cursor-pointer text-left\">")
                  .append(escapeHtml(bl.url)).append("</td>")
                  .append("<td class=\"px-3 py-2 text-left text-red-600 font-semibold\">").append(escapeHtml(bl.status)).append("</td></tr>\n");
            }
            sb.append("</tbody></table></div></section>\n");
        }
        sb.append("</div></body></html>\n");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("WebsiteQA2HTML.html"))) {
            writer.write(sb.toString());
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String cssColorToHex(String cssColor) {
        if (cssColor == null) return "";
        if (cssColor.trim().startsWith("#")) return cssColor;
        try {
            if (cssColor.startsWith("rgb(") || cssColor.startsWith("rgba(")) {
                String nums = cssColor.substring(cssColor.indexOf('(') + 1, cssColor.indexOf(')'));
                String[] parts = nums.split(",");
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());
                return String.format("#%02x%02x%02x", r, g, b);
            }
        } catch (Exception e) {
            return cssColor;
        }
        return cssColor;
    }

    static class WebsiteData {
        String url;
        String title;
        String metaDescription;
        Map<String, List<HeadingElement>> headers;
        List<ButtonOrLink> buttons;
        List<ParagraphStyle> paragraphs;
        List<GrammarIssue> grammarIssues;
        List<BrokenLink> brokenLinks;

        public WebsiteData(String url, String title, String metaDescription,
                          Map<String, List<HeadingElement>> headers, List<ButtonOrLink> buttons,
                          List<ParagraphStyle> paragraphs, List<GrammarIssue> grammarIssues,
                          List<BrokenLink> brokenLinks) {
            this.url = url;
            this.title = title;
            this.metaDescription = metaDescription;
            this.headers = headers;
            this.buttons = buttons;
            this.paragraphs = paragraphs;
            this.grammarIssues = grammarIssues;
            this.brokenLinks = brokenLinks;
        }
    }

    static class HeadingElement {
        String tag, text, fontFamily, fontSize, fontWeight, lineHeight, letterSpacing, color;

        public HeadingElement(String tag, String text, String fontFamily, String fontSize, String fontWeight,
                              String lineHeight, String letterSpacing, String color) {
            this.tag = tag;
            this.text = text;
            this.fontFamily = fontFamily;
            this.fontSize = fontSize;
            this.fontWeight = fontWeight;
            this.lineHeight = lineHeight;
            this.letterSpacing = letterSpacing;
            this.color = color;
        }
    }

    static class ButtonOrLink {
        String type, text, buttonColor, fontSize, fontWeight, padding, borderRadius, href, textColor;

        public ButtonOrLink(String type, String text, String buttonColor, String fontSize, String fontWeight,
                            String padding, String borderRadius, String href, String textColor) {
            this.type = type;
            this.text = text;
            this.buttonColor = buttonColor;
            this.fontSize = fontSize;
            this.fontWeight = fontWeight;
            this.padding = padding;
            this.borderRadius = borderRadius;
            this.href = href;
            this.textColor = textColor;
        }
    }

    static class ParagraphStyle {
        String text, fontFamily, fontSize, fontWeight, lineHeight, letterSpacing, color;

        public ParagraphStyle(String text, String fontFamily, String fontSize, String fontWeight,
                              String lineHeight, String letterSpacing, String color) {
            this.text = text;
            this.fontFamily = fontFamily;
            this.fontSize = fontSize;
            this.fontWeight = fontWeight;
            this.lineHeight = lineHeight;
            this.letterSpacing = letterSpacing;
            this.color = color;
        }
    }

    static class GrammarIssue {
        String context, suggestion, message;

        public GrammarIssue(String context, String suggestion, String message) {
            this.context = context;
            this.suggestion = suggestion;
            this.message = message;
        }
    }

    static class BrokenLink {
        String text, url, status;

        public BrokenLink(String text, String url, String status) {
            this.text = text;
            this.url = url;
            this.status = status;
        }
    }

    public static void main(String[] args) throws Exception {
        muteLogs();

        WebsiteQA2HTML qa = new WebsiteQA2HTML();
        qa.runQAAudit("https://www.havis.com/");
    }
}