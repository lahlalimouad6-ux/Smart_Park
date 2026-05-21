package com.smartpark.e2e.support;

import com.smartpark.e2e.E2EConfig;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.time.Duration;

public final class WebDriverFactory {
    private WebDriverFactory() {}

    public static WebDriver create() {
        ChromeOptions options = new ChromeOptions();
        if (E2EConfig.HEADLESS) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=1365,900");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));
        return driver;
    }
}
