package com.smartpark.e2e.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ParkingDetailPage extends BasePage {
    private static final DateTimeFormatter FRONTEND_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    public ParkingDetailPage(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    public void waitLoaded() {
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(text(),'Réservation')]")));
    }

    public void selectSpotByTitle(String exactTitle) {
        clickable(By.cssSelector("button[title='" + cssEscape(exactTitle) + "']")).click();
    }

    public void setDates(LocalDateTime debut, LocalDateTime fin) {
        List<WebElement> inputs = driver.findElements(By.cssSelector("input[type='datetime-local']"));
        if (inputs.size() < 2) {
            throw new IllegalStateException("Expected 2 datetime-local inputs, found " + inputs.size());
        }
        setDateValue(inputs.get(0), debut);
        setDateValue(inputs.get(1), fin);
    }

    public void confirmReservation() {
        clickable(By.xpath("//button[contains(.,'Confirmer la réservation')]")).click();
    }

    private void setDateValue(WebElement input, LocalDateTime value) {
        input.click();
        input.sendKeys(Keys.chord(Keys.CONTROL, "a"), value.format(FRONTEND_DT));
    }

    private static String cssEscape(String v) {
        return v.replace("\\", "\\\\").replace("'", "\\'");
    }
}
