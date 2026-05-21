package com.smartpark.e2e.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public final class RegisterPage extends BasePage {
    public RegisterPage(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    public void register(String prenom, String nom, String email, String password) {
        clickable(By.cssSelector("input[name='prenom']")).sendKeys(Keys.chord(Keys.CONTROL, "a"), prenom);
        clickable(By.cssSelector("input[name='nom']")).sendKeys(Keys.chord(Keys.CONTROL, "a"), nom);
        clickable(By.cssSelector("input[name='email']")).sendKeys(Keys.chord(Keys.CONTROL, "a"), email);
        clickable(By.cssSelector("input[name='password']")).sendKeys(Keys.chord(Keys.CONTROL, "a"), password);
        clickable(By.cssSelector("button[type='submit']")).click();
    }

    public void waitForSuccess() {
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".bg-green-50")));
    }

    public void waitRedirectToLogin() {
        waitUrlContains("/login");
    }
}
