package com.smartpark.e2e.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public final class ReservationsPage extends BasePage {
    public ReservationsPage(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    public void waitLoaded() {
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(text(),'Accès') and contains(text(),'QR')]")));
    }

    public boolean hasReservationForSpot(String spotNumeroPlace) {
        return driver.findElements(By.xpath("//*[contains(text(),'Place " + spotNumeroPlace + "')]")).size() > 0;
    }

    public boolean canCancelVisible() {
        return driver.findElements(By.xpath("//button[contains(.,'Annuler la réservation')]")).size() > 0;
    }

    public void cancelSelectedReservation() {
        clickable(By.xpath("//button[contains(.,'Annuler la réservation')]")).click();
        driver.switchTo().alert().accept();
        wait.until(d -> d.findElements(By.xpath("//button[contains(.,'Annuler la réservation')]")).isEmpty());
    }
}
