package Full_HTML;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import io.github.bonigarcia.wdm.WebDriverManager;

public class CountPTags {

	public static void main(String[] args) {
		WebDriverManager.chromedriver().setup();
		WebDriver driver;
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--disable-gpu");
		options.addArguments("--remote-allow-origins=*"); // NEW LINE
		driver = new ChromeDriver(options);

		driver.manage().window().maximize();
		driver.get("https://www.havis.com/");

		List<WebElement> pTags = driver.findElements(By.xpath("//p"));

		System.out.println("Total <p> tags: " + pTags.size());

		driver.quit();
	}
}