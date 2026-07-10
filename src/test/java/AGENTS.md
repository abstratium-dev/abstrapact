# Test Agents Guide

## Tearing Down Test Data

Tests that create persistent data should clean up in an `@AfterEach` method using `TestDataCleaner`:

```java
@Inject
TestDataCleaner cleaner;

@AfterEach
void tearDown() throws Exception {
    cleaner.deleteAll();
}
```

`TestDataCleaner` is located at `dev.abstratium.test.TestDataCleaner`.

It removes all `Contract`, `ProductInstance`, and `ProductDefinition` entities using `em.remove()`, which triggers JPA `CascadeType.REMOVE` to automatically clean up all dependent child entities. Do not write manual bulk `DELETE` JPQL queries — JPA cascading handles the correct deletion order.
