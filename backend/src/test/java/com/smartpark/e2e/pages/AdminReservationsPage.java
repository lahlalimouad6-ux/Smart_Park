package com.smartpark.e2e.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public final class AdminReservationsPage extends BasePage {
    public AdminReservationsPage(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    public void waitLoaded() {
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//h1[contains(.,'Réservations')]")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("table")));
    }

    public void search(String query) {
        clickable(By.cssSelector("input[placeholder^='Rechercher']")).sendKeys(Keys.chord(Keys.CONTROL, "a"), query);
        wait.until(d -> true);
    }

    public boolean hasRowContaining(String text) {
        String literal = xpathLiteral(text);
        return driver.findElements(By.xpath("//table//*[contains(., " + literal + ")]")).size() > 0;
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
