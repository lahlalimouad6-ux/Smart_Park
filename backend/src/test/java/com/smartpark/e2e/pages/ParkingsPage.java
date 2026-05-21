package com.smartpark.e2e.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public final class ParkingsPage extends BasePage {
    public ParkingsPage(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    public void waitLoaded() {
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//h1[contains(.,'Trouver un parking')]")));
    }

    public void search(String term) {
        clickable(By.cssSelector("input[placeholder^='Rechercher par nom']")).sendKeys(Keys.chord(Keys.CONTROL, "a"), term);
    }

    public boolean hasParkingCard(String parkingName) {
        return driver.findElements(By.xpath("//*[contains(@class,'sp-card')][.//*[contains(., " + xpathLiteral(parkingName) + ")]]")).size() > 0;
    }

    public void openParkingByName(String parkingName) {
        clickable(By.xpath("//*[contains(@class,'sp-card')][.//*[contains(., " + xpathLiteral(parkingName) + ")]]//a[contains(@href,'/parkings/')]")).click();
    }

    private static String xpathLiteral(String s) {
        if (s == null) return "''";
        if (!s.contains("'")) return "'" + s + "'";
        if (!s.contains("\"")) return "\"" + s + "\"";
        StringBuilder out = new StringBuilder("concat(");
        boolean first = true;
        for (char c : s.toCharArray()) {
            if (!first) out.append(", ");
            first = false;
            if (c == '\'') out.append("\"'\"");
            else out.append("'").append(c).append("'");
        }
        out.append(")");
        return out.toString();
    }
}
