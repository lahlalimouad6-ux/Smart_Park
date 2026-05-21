package com.smartpark.e2e.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

public final class LoginPage extends BasePage {
    public LoginPage(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    public void login(String email, String password) {
        clickable(By.id("email-address")).sendKeys(Keys.chord(Keys.CONTROL, "a"), email);
        clickable(By.id("password")).sendKeys(Keys.chord(Keys.CONTROL, "a"), password);
        clickable(By.cssSelector("button[type='submit']")).click();
    }

    public void waitForRedirectToParkingsOrAdmin() {
        wait.until(d -> {
            String url = d.getCurrentUrl();
            return url.contains("/parkings") || url.contains("/admin");
        });
    }

    public String readErrorMessageIfAny() {
        try {
            return find(By.cssSelector(".bg-red-50 p")).getText();
        } catch (Exception e) {
            return null;
        }
    }
}
