# L02 — Money value object (kobo, rounding) + tests

Presented: 2026-10-17 · Status: learner typing · Verified in Claude's scratchpad: 18/18 tests green, full build green, Break-it verified

## Review of L01B (2026-10-17)

- ✅ All 7 typed files are identical to the verified versions; `mvn verify` passes.
- ❌ `platform/platform-observability/src/test/java/...`: the 3 test files are missing → type them (in `L01B-json-logs-correlation-id-tracing.md`).
- ❌ `.gitattributes`: still 0 bytes → type it, then `git add --renormalize .`
- ❌ `docs/adr/0002-service-level-objectives.md`: missing → type it (L01A lesson, Appendix).
- ❌ `mvnw`: not generated → `mvn -N wrapper:wrapper "-Dmaven=3.10.0"`
- ❌ L01B not committed, no remote → see Checkpoint.
- ❌ Docker: still `Virtual Machine Platform not enabled` (no restart since 2026-10-16).

## 1. Story

Tolu sends ₦0.10 ten times. A `double` ledger says she sent ₦0.9999999999999999. At ten million transfers a day, those lost fractions become a reconciliation break nobody can explain.

## 2. Goal

A `Money` type that is exact, refuses to round silently, refuses to mix currencies, and fails loudly on overflow.

## 3. Files

```
fintechdemo/
├── pom.xml                                            ← CHANGED
└── platform/
    ├── platform-money/                                ← NEW module
    │   ├── pom.xml                                    ← NEW
    │   └── src/
    │       ├── main/
    │       │   └── java/
    │       │       └── com/fintechdemo/platform/money/
    │       │           ├── Currency.java                    ← NEW
    │       │           ├── CurrencyMismatchException.java   ← NEW
    │       │           ├── InvalidAmountException.java      ← NEW
    │       │           └── Money.java                       ← NEW
    │       └── test/
    │           └── java/
    │               └── com/fintechdemo/platform/money/
    │                   ├── CurrencyTest.java                ← NEW
    │                   └── MoneyTest.java                   ← NEW
    └── platform-observability/                        (unchanged)
```

| # | File | New / changed | Purpose |
| --- | --- | --- | --- |
| 1 | `pom.xml` | changed | Adds the module and its managed version |
| 2 | `platform/platform-money/pom.xml` | new | Plain Java module: test dependencies only |
| 3 | `Currency.java` | new | Supported currencies and their minor-unit digits |
| 4 | `CurrencyMismatchException.java` | new | NGN + USD is a bug |
| 5 | `InvalidAmountException.java` | new | ₦10.505 can't exist |
| 6 | `Money.java` | new | The value object |
| 7 | `CurrencyTest.java` | new | Our digits match ISO 4217 |
| 8 | `MoneyTest.java` | new | 16 cases: exactness, rounding, overflow, mixing |

## 4. Code

### `pom.xml` (root, CHANGED: whole file)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- Spring Boot's parent gives every module tested library versions and plugin defaults. -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.1</version>
        <relativePath/>
    </parent>

    <groupId>com.fintechdemo</groupId>
    <artifactId>fintechdemo-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>fintechdemo-parent</name>
    <description>FINTECHDEMO P2P payments core - parent POM: modules, versions, plugins only</description>

    <!-- Modules are added lesson by lesson. -->
    <modules>
        <module>platform/platform-money</module>
        <module>platform/platform-observability</module>
    </modules>

    <properties>
        <java.version>25</java.version>
        <required.java.range>[25,26)</required.java.range>
        <required.maven.range>[3.9.9,)</required.maven.range>
    </properties>

    <!-- Our own modules: services declare them without a version. -->
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.fintechdemo</groupId>
                <artifactId>platform-money</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.fintechdemo</groupId>
                <artifactId>platform-observability</artifactId>
                <version>${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <!-- Fail fast on the wrong JDK or Maven, on Windows, macOS and CI alike. -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-enforcer-plugin</artifactId>
                <executions>
                    <execution>
                        <id>enforce-build-environment</id>
                        <goals>
                            <goal>enforce</goal>
                        </goals>
                        <configuration>
                            <rules>
                                <requireJavaVersion>
                                    <version>${required.java.range}</version>
                                    <message>FINTECHDEMO builds on JDK 25 (LTS). macOS: brew install --cask temurin@25</message>
                                </requireJavaVersion>
                                <requireMavenVersion>
                                    <version>${required.maven.range}</version>
                                    <message>Use the Maven wrapper: ./mvnw (macOS) or .\mvnw.cmd (Windows).</message>
                                </requireMavenVersion>
                                <banDuplicatePomDependencyVersions/>
                            </rules>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

### `platform/platform-money/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.fintechdemo</groupId>
        <artifactId>fintechdemo-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>platform-money</artifactId>
    <name>platform-money</name>
    <description>Money and Currency value objects. Plain Java: no Spring, no dependencies.</description>

    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

### `platform/platform-money/src/main/java/com/fintechdemo/platform/money/Currency.java`

```java
package com.fintechdemo.platform.money;

/**
 * Currencies the platform supports. Adding one is a deliberate decision, never a typo in a request.
 * The number of minor-unit digits comes from ISO 4217 (NGN: 100 kobo = 1 naira).
 */
public enum Currency {
    NGN(2),
    USD(2);

    private final int minorUnitDigits;

    Currency(int minorUnitDigits) {
        this.minorUnitDigits = minorUnitDigits;
    }

    public int minorUnitDigits() {
        return minorUnitDigits;
    }
}
```

### `platform/platform-money/src/main/java/com/fintechdemo/platform/money/CurrencyMismatchException.java`

```java
package com.fintechdemo.platform.money;

/** Thrown when two amounts in different currencies are combined or compared. */
public final class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("Currency mismatch: expected " + expected + " but got " + actual);
    }
}
```

### `platform/platform-money/src/main/java/com/fintechdemo/platform/money/InvalidAmountException.java`

```java
package com.fintechdemo.platform.money;

/** Thrown when an amount cannot be represented exactly in a currency's minor units. */
public final class InvalidAmountException extends RuntimeException {

    public InvalidAmountException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### `platform/platform-money/src/main/java/com/fintechdemo/platform/money/Money.java`

```java
package com.fintechdemo.platform.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An exact amount of money: a whole number of minor units (kobo for NGN) plus its currency.
 * No float or double anywhere. Arithmetic fails loudly on overflow or mixed currencies.
 */
public record Money(long minorUnits, Currency currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    /** Converts a major-unit amount from an API edge (e.g. 10000.50 naira). Never rounds silently. */
    public static Money ofMajor(BigDecimal amount, Currency currency) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        try {
            long minorUnits = amount.movePointRight(currency.minorUnitDigits()).longValueExact();
            return new Money(minorUnits, currency);
        } catch (ArithmeticException notExact) {
            throw new InvalidAmountException(
                    amount + " is not an exact " + currency + " amount (max "
                            + currency.minorUnitDigits() + " decimal places)", notExact);
        }
    }

    public BigDecimal toMajor() {
        return BigDecimal.valueOf(minorUnits, currency.minorUnitDigits());
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(minorUnits, other.minorUnits), currency);
    }

    /** Applies a rate (fee percentage, tax rate). The caller must choose how to round. */
    public Money times(BigDecimal factor, RoundingMode rounding) {
        Objects.requireNonNull(factor, "factor");
        Objects.requireNonNull(rounding, "rounding");
        BigDecimal exact = BigDecimal.valueOf(minorUnits).multiply(factor);
        return new Money(exact.setScale(0, rounding).longValueExact(), currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return minorUnits > other.minorUnits;
    }

    public boolean isLessThan(Money other) {
        requireSameCurrency(other);
        return minorUnits < other.minorUnits;
    }

    public boolean isZero() {
        return minorUnits == 0;
    }

    public boolean isPositive() {
        return minorUnits > 0;
    }

    public boolean isNegative() {
        return minorUnits < 0;
    }

    @Override
    public String toString() {
        return currency + " " + toMajor().toPlainString();
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (other.currency != currency) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }
}
```

### `platform/platform-money/src/test/java/com/fintechdemo/platform/money/CurrencyTest.java`

```java
package com.fintechdemo.platform.money;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CurrencyTest {

    // The JDK ships the ISO 4217 table: our hand-written digits must agree with it.
    @ParameterizedTest
    @EnumSource(Currency.class)
    void minorUnitDigitsMatchIso4217(Currency currency) {
        int iso4217Digits = java.util.Currency.getInstance(currency.name()).getDefaultFractionDigits();

        assertThat(currency.minorUnitDigits()).isEqualTo(iso4217Digits);
    }
}
```

### `platform/platform-money/src/test/java/com/fintechdemo/platform/money/MoneyTest.java`

```java
package com.fintechdemo.platform.money;

import static com.fintechdemo.platform.money.Currency.NGN;
import static com.fintechdemo.platform.money.Currency.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    private static Money naira(String amount) {
        return Money.ofMajor(new BigDecimal(amount), NGN);
    }

    @Test
    void convertsNairaToKobo() {
        assertThat(naira("10000.50").minorUnits()).isEqualTo(1_000_050L);
    }

    @Test
    void acceptsTrailingZeros() {
        assertThat(naira("10.500").minorUnits()).isEqualTo(1_050L);
    }

    @Test
    void rejectsFractionsOfAKobo() {
        assertThatThrownBy(() -> naira("10.505"))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("max 2 decimal places");
    }

    @Test
    void convertsBackToNairaWithTwoDecimals() {
        assertThat(new Money(1_000_050, NGN).toMajor()).isEqualByComparingTo("10000.50");
        assertThat(new Money(1_000_050, NGN)).hasToString("NGN 10000.50");
    }

    @Test
    void addingTenKoboTenTimesIsExactlyOneNaira() {
        Money total = Money.zero(NGN);
        for (int i = 0; i < 10; i++) {
            total = total.plus(naira("0.10"));
        }

        assertThat(total).isEqualTo(naira("1.00"));
    }

    @Test
    void subtracts() {
        assertThat(naira("100.00").minus(naira("0.01"))).isEqualTo(naira("99.99"));
    }

    @Test
    void equalityDoesNotDependOnHowTheAmountWasWritten() {
        assertThat(new BigDecimal("10.5")).isNotEqualTo(new BigDecimal("10.50"));
        assertThat(naira("10.5")).isEqualTo(naira("10.50"));
    }

    @Test
    void refusesToMixCurrencies() {
        assertThatThrownBy(() -> naira("1.00").plus(Money.ofMajor(BigDecimal.ONE, USD)))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessage("Currency mismatch: expected NGN but got USD");
    }

    @Test
    void failsOnOverflowInsteadOfWrappingAround() {
        Money max = new Money(Long.MAX_VALUE, NGN);

        assertThatThrownBy(() -> max.plus(new Money(1, NGN))).isInstanceOf(ArithmeticException.class);
    }

    // 7.5% of NGN 11.00 = 82.5 kobo exactly: the rounding mode decides who keeps the half kobo.
    @ParameterizedTest
    @CsvSource({"HALF_UP, 83", "HALF_EVEN, 82", "DOWN, 82", "UP, 83"})
    void appliesARateWithTheRoundingModeTheCallerChooses(RoundingMode rounding, long expectedKobo) {
        Money result = naira("11.00").times(new BigDecimal("0.075"), rounding);

        assertThat(result.minorUnits()).isEqualTo(expectedKobo);
    }

    @Test
    void refusesToRoundWhenToldRoundingIsUnnecessary() {
        assertThatThrownBy(() -> naira("11.00").times(new BigDecimal("0.075"), RoundingMode.UNNECESSARY))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void compares() {
        assertThat(naira("10.01").isGreaterThan(naira("10.00"))).isTrue();
        assertThat(naira("10.00").isLessThan(naira("10.01"))).isTrue();
        assertThat(naira("0.00").isZero()).isTrue();
        assertThat(naira("-0.01").isNegative()).isTrue();
        assertThat(naira("0.01").isPositive()).isTrue();
    }

    @Test
    void requiresACurrency() {
        assertThatThrownBy(() -> new Money(100, null)).isInstanceOf(NullPointerException.class);
    }
}
```

## 5. Why

- **`long` kobo**: exact, no allocation, maps to `BIGINT`; max ≈ ₦92 quadrillion. Rejected: `double` (`0.1 + 0.2 = 0.30000000000000004`), and `BigDecimal` everywhere (slower, and `10.5` ≠ `10.50` in `equals`).
- **BigDecimal only at the edges** (`ofMajor`, `toMajor`) and for rates (`times`).
- **No silent rounding**: `10.505` is rejected. For rates the caller must name a `RoundingMode`; L19 picks it from official sources.
- **`Math.addExact`**: overflow throws instead of wrapping around to a negative balance.
- **Own `Currency` enum**: only currencies we support; a test checks the digits against the JDK's ISO 4217 data.
- **Zero dependencies**: every service's plain-Java domain code can use it.

The 0.075 rate in the tests is only an example; it is not a claim about any real tax or fee.

## 6. Run

```powershell
.\mvnw.cmd -pl platform/platform-money verify
```

```
[INFO] Tests run: 2, Failures: 0 ... CurrencyTest
[INFO] Tests run: 16, Failures: 0 ... MoneyTest
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

✅ verified. The full build `.\mvnw.cmd verify` is also green.

## 7. Break it

Add this test to `MoneyTest` temporarily:

```java
    @Test
    void breakIt() {
        Money.ofMajor(new BigDecimal(0.1), NGN);   // a double sneaks in
    }
```

Run it. ✅ Verified:

```
InvalidAmountException: 0.1000000000000000055511151231257827021181583404541015625 is not an exact NGN amount (max 2 decimal places)
```

`new BigDecimal(0.1)` carries the double's error with it, and `Money` refuses it instead of rounding. Delete the test afterwards.

## 8. Interview line

"Money is a value object holding a long count of kobo plus a currency, so arithmetic is exact and maps straight to a BIGINT column. BigDecimal is used only at the API edge and for rates, where the caller must choose the rounding mode, and nothing ever rounds silently. Overflow and mixed currencies throw instead of corrupting a balance."

## 9. Checkpoint

1. Fix the review items above (L01B tests, `.gitattributes`, ADR-0002, wrapper).
2. Type the 8 files, then run `.\mvnw.cmd verify` (all modules green). Do Break it, then delete the test.
3. Commit and push:
   ```powershell
   git add .
   git add --renormalize .
   git add --chmod=+x mvnw
   git commit -m "L01 fixes + L01B observability + L02 Money value object"
   git remote add origin https://github.com/Sholana1/fintechdemo.git
   git push -u origin main
   ```
   (Create the private `fintechdemo` repo at https://github.com/new first.)
4. Type **NEXT** → L03: error model, validation, problem+json.

## My notes

(learner: add your own notes here)
