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

public class WebsiteQA2HTMLWeam {
    private WebDriver driver;
    private WebDriverWait wait;

    // Call this with the target URL
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

        // Meta tags (SEO)
        String metaDescription = "";
        try {
            WebElement metaDesc = driver.findElement(By.xpath("//meta[@name='description']"));
            metaDescription = metaDesc.getAttribute("content");
        } catch (NoSuchElementException ex) {
            metaDescription = "MISSING";
        }

        // Header tags and styles
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

        // All buttons and links
        List<ButtonOrLink> buttons = new ArrayList<>();
        for (WebElement btn : driver.findElements(By.tagName("button"))) {
            buttons.add(extractButtonOrLink(btn, "button"));
        }
        for (WebElement link : driver.findElements(By.tagName("a"))) {
            buttons.add(extractButtonOrLink(link, "a"));
        }

        // Paragraphs
        List<ParagraphStyle> paragraphs = new ArrayList<>();
        for (WebElement para : driver.findElements(By.tagName("p"))) {
            paragraphs.add(extractParagraphStyle(para));
        }

        // Visible text
        String visibleText = driver.findElement(By.tagName("body")).getText();

        // Grammar and spelling check
        List<String> grammarIssues = grammarCheck(visibleText);

        // Broken link check
        List<BrokenLink> brokenLinks = new ArrayList<>();
        for (WebElement link : driver.findElements(By.tagName("a"))) {
            String linkUrl = link.getAttribute("href");
            if (linkUrl != null && !linkUrl.startsWith("javascript")) {
                int status = getHttpStatus(linkUrl);
                if (status >= 400) {
                    brokenLinks.add(new BrokenLink(linkUrl, getElementXPath(driver, link)));
                }
            }
        }

        // Generate report in HTML Dashboard style
        generateHTMLReport(
                exactUrl,
                title,
                metaDescription,
                headers,
                buttons,
                paragraphs,
                grammarIssues,
                brokenLinks
        );

        driver.quit();
    }

    // Extract header styles as a structured object
    private HeadingElement extractHeadingStyle(WebElement el, String tag) {
        return new HeadingElement(
                tag,
                el.getText(),
                el.getCssValue("font-size"),
                el.getCssValue("font-family"),
                el.getCssValue("font-weight"),
                el.getCssValue("color"),
                el.getCssValue("line-height"),
                el.getCssValue("letter-spacing")
        );
    }

    // Button/link style/data extraction
    private ButtonOrLink extractButtonOrLink(WebElement el, String type) {
        return new ButtonOrLink(
                type,
                el.getText(),
                el.getCssValue("font-size"),
                el.getCssValue("font-family"),
                el.getCssValue("font-weight"),
                el.getCssValue("font-style"),
                el.getCssValue("line-height"),
                el.getCssValue("letter-spacing"),
                el.getAttribute("href")
        );
    }

    private ParagraphStyle extractParagraphStyle(WebElement el) {
        return new ParagraphStyle(
                el.getText(),
                el.getCssValue("font-size"),
                el.getCssValue("font-family"),
                el.getCssValue("font-weight"),
                el.getCssValue("color"),
                el.getCssValue("line-height"),
                el.getCssValue("letter-spacing")
        );
    }

    // Grammar and spelling check using LanguageTool
    private List<String> grammarCheck(String text) throws IOException {
        JLanguageTool langTool = new JLanguageTool(new AmericanEnglish());
        List<RuleMatch> matches = langTool.check(text);
        List<String> issues = new ArrayList<>();
        for (RuleMatch match : matches) {
            issues.add("Potential error at line " + match.getLine() +
                    ", column " + match.getColumn() +
                    ": " + match.getMessage() +
                    " [suggestion: " + match.getSuggestedReplacements() + "]");
        }
        return issues;
    }

    // HTTP status check for links (broken link detection)
    private int getHttpStatus(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            return connection.getResponseCode();
        } catch (Exception e) {
            return 400;
        }
    }

    // Get XPath for reporting location of broken elements
    private String getElementXPath(WebDriver driver, WebElement element) {
        String jsCode = "function absoluteXPath(element) {" +
                "var comp, comps = [];" +
                "var parent = null;" +
                "var xpath = '';" +
                "var getPos = function(element) {" +
                "var position = 1, curNode;" +
                "if (element.nodeType == Node.ATTRIBUTE_NODE) {" +
                "return null;" +
                "}" +
                "for (curNode = element.previousSibling; curNode; curNode = curNode.previousSibling) {" +
                "if (curNode.nodeName == element.nodeName) {" +
                "++position;" +
                "}" +
                "}" +
                "return position;" +
                "};" +
                "if (element instanceof Document) {" +
                "return '/';" +
                "}" +
                "for (; element && !(element instanceof Document); element = element.nodeType == Node.ATTRIBUTE_NODE ? element.ownerElement : element.parentNode) {" +
                "comp = comps[comps.length] = {};" +
                "switch (element.nodeType) {" +
                "case Node.TEXT_NODE:" +
                "comp.name = 'text()';" +
                "break;" +
                "case Node.ATTRIBUTE_NODE:" +
                "comp.name = '@' + element.nodeName;" +
                "break;" +
                "case Node.PROCESSING_INSTRUCTION_NODE:" +
                "comp.name = 'processing-instruction()';" +
                "break;" +
                "case Node.COMMENT_NODE:" +
                "comp.name = 'comment()';" +
                "break;" +
                "case Node.ELEMENT_NODE:" +
                "comp.name = element.nodeName;" +
                "break;" +
                "}" +
                "comp.position = getPos(element);" +
                "}" +
                "for (var i = comps.length - 1; i >= 0; i--) {" +
                "comp = comps[i];" +
                "xpath += '/' + comp.name.toLowerCase();" +
                "if (comp.position !== null && comp.position > 1) {" +
                "xpath += '[' + comp.position + ']';" +
                "}" +
                "}" +
                "return xpath;" +
                "}" +
                "return absoluteXPath(arguments[0]);";
        return (String) ((JavascriptExecutor) driver).executeScript(jsCode, element);
    }

    // Escapes HTML special chars for safe output
    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // Generate HTML Dashboard Report
    private void generateHTMLReport(String url, String title, String metaDescription,
                                   Map<String, List<HeadingElement>> headers,
                                   List<ButtonOrLink> buttons,
                                   List<ParagraphStyle> paragraphs,
                                   List<String> grammarIssues,
                                   List<BrokenLink> brokenLinks) throws IOException
    {
        StringBuilder sb = new StringBuilder();

        // HTML HEAD and DASHBOARD TABLE
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
          .append("  <meta charset=\"UTF-8\" />\n")
          .append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"/>\n")
          .append("  <title>QA Automation Dashboard</title>\n")
          .append("  <style>\n")
          .append("    body { font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px; }\n")
          .append("    h1 { text-align: center; color: #333; }\n")
          .append("    table { width: 100%; border-collapse: collapse; margin-top: 30px; }\n")
          .append("    th, td { padding: 12px 15px; border: 1px solid #ddd; text-align: left; }\n")
          .append("    th { background-color: #007bff; color: white; }\n")
          .append("    tr:nth-child(even) { background-color: #f9f9f9; }\n")
          .append("    tr:hover { background-color: #f1f1f1; }\n")
          .append("    ul { margin: 0; padding-left: 20px; }\n")
          .append("    .issue { color: red; font-size: 12px; }\n")
          .append("    .broken { color: #a00; font-size: 12px; }\n")
          .append("  </style>\n")
          .append("</head>\n<body>\n")
          .append("  <h1>QA Automation Dashboard</h1>\n")
          .append("  <table>\n<thead>\n<tr>\n")
          .append("  <th>URL</th>\n")
          .append("  <th>Page Title</th>\n")
          .append("  <th>Meta Description</th>\n")
          .append("  <th>Buttons/Link Text & URLs</th>\n")
          .append("  <th>H1 Tags</th>\n")
          .append("  <th>H2 Tags</th>\n")
          .append("  <th>H3 Tags</th>\n")
          .append("  <th>H4 Tags</th>\n")
          .append("  <th>H5 Tags</th>\n")
          .append("  <th>H6 Tags</th>\n")
          .append("</tr>\n</thead><tbody>\n");

        // Only one row for this script (extendable)
        sb.append("<tr>\n");

        // URL
        sb.append("<td>").append(escape(url)).append("</td>\n");
        // Page Title
        sb.append("<td>").append(escape(title)).append("</td>\n");
        // Meta Description
        sb.append("<td>").append(escape(metaDescription)).append("</td>\n");

        // Buttons/Links as <ul>
        sb.append("<td><ul>");
        for (ButtonOrLink b : buttons) {
            if (b.href != null && !b.href.isEmpty()) {
                sb.append("<li><a href=\"").append(escape(b.href)).append("\">")
                  .append(escape(b.text))
                  .append("</a></li>");
            } else {
                sb.append("<li>").append(escape(b.text)).append("</li>");
            }
        }
        sb.append("</ul></td>\n");

        // H1-H6 tags as multi-line
        for (int i = 1; i <= 6; i++) {
            sb.append("<td>");
            List<HeadingElement> section = headers.get("h" + i);
            if (section != null && !section.isEmpty()) {
                for (HeadingElement el : section) {
                    sb.append(escape(el.text)).append("<br/>");
                }
            }
            sb.append("</td>\n");
        }

        sb.append("</tr>\n</tbody></table>\n");

        // ======= Additional Details =======

        sb.append("<h2>Paragraphs:</h2>");
        for (ParagraphStyle p : paragraphs) {
            sb.append("<div>").append(escape(p.text))
              .append(" [font: ").append(escape(p.fontSize)).append("/")
              .append(escape(p.fontFamily)).append("/")
              .append(escape(p.fontWeight)).append("/")
              .append(escape(p.color)).append("]</div>");
        }

        if (!grammarIssues.isEmpty()) {
            sb.append("<h2>Grammar and Spelling Issues:</h2>");
            for (String issue : grammarIssues) {
                sb.append("<div class='issue'>").append(escape(issue)).append("</div>");
            }
        }

        if (!brokenLinks.isEmpty()) {
            sb.append("<h2>Broken Links:</h2>");
            for (BrokenLink bl : brokenLinks) {
                sb.append("<div class='broken'>URL: ").append(escape(bl.url))
                  .append(" [DOM Path: ").append(escape(bl.domPath)).append("]</div>");
            }
        }

        sb.append("</body></html>\n");

        BufferedWriter writer = new BufferedWriter(new FileWriter("WebsiteQA2HTMLWeam.html"));
        writer.write(sb.toString());
        writer.close();
    }

    // Helper classes
    class HeadingElement {
        String tag, text, fontSize, fontFamily, fontWeight, color, lineHeight, letterSpacing;

        public HeadingElement(String tag, String text, String fontSize, String fontFamily,
                              String fontWeight, String color, String lineHeight, String letterSpacing) {
            this.tag = tag; this.text = text; this.fontSize = fontSize; this.fontFamily = fontFamily;
            this.fontWeight = fontWeight; this.color = color; this.lineHeight = lineHeight;
            this.letterSpacing = letterSpacing;
        }
    }

    class ButtonOrLink {
        String type, text, fontSize, fontFamily, fontWeight, fontStyle, lineHeight, letterSpacing, href;

        public ButtonOrLink(String type, String text, String fontSize, String fontFamily,
                            String fontWeight, String fontStyle, String lineHeight,
                            String letterSpacing, String href) {
            this.type = type; this.text = text; this.fontSize = fontSize; this.fontFamily = fontFamily;
            this.fontWeight = fontWeight; this.fontStyle = fontStyle; this.lineHeight = lineHeight;
            this.letterSpacing = letterSpacing; this.href = href;
        }
    }

    class ParagraphStyle {
        String text, fontSize, fontFamily, fontWeight, color, lineHeight, letterSpacing;

        public ParagraphStyle(String text, String fontSize, String fontFamily, String fontWeight,
                              String color, String lineHeight, String letterSpacing) {
            this.text = text; this.fontSize = fontSize; this.fontFamily = fontFamily;
            this.fontWeight = fontWeight; this.color = color;
            this.lineHeight = lineHeight; this.letterSpacing = letterSpacing;
        }
    }

    class BrokenLink {
        String url, domPath;

        public BrokenLink(String url, String domPath) {
            this.url = url; this.domPath = domPath;
        }
    }

    // MAIN — example usage
    public static void main(String[] args) throws Exception {
        new WebsiteQA2HTML().runQAAudit("https://www.havis.com/about-havis/");
    }
}