package com.nortal.library.api;

import static org.junit.jupiter.api.Assertions.*;

import com.nortal.library.api.dto.BookResponse;
import com.nortal.library.api.dto.BooksResponse;
import com.nortal.library.api.dto.BorrowRequest;
import com.nortal.library.api.dto.DeleteMemberRequest;
import com.nortal.library.api.dto.ReserveRequest;
import com.nortal.library.api.dto.ResultResponse;
import com.nortal.library.api.dto.ResultWithNextResponse;
import com.nortal.library.api.dto.ReturnRequest;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ApiRulesIntegrationTest {

  @LocalServerPort int port;

  private final TestRestTemplate rest = new TestRestTemplate();

  @Test
  void cannotBorrowAlreadyLoanedBook() {
    ResultResponse borrow1 =
        rest.postForObject(url("/api/borrow"), new BorrowRequest("b1", "m1"), ResultResponse.class);
    assertNotNull(borrow1);
    assertTrue(borrow1.ok());

    ResultResponse borrow2 =
        rest.postForObject(url("/api/borrow"), new BorrowRequest("b1", "m2"), ResultResponse.class);
    assertNotNull(borrow2);
    assertFalse(borrow2.ok(), "Second borrow should fail when already loaned");
  }

  @Test
  void returnRejectedWhenNotCurrentBorrower() {
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b2", "m1"), ResultResponse.class);

    ResultWithNextResponse wrongReturn =
        rest.postForObject(
            url("/api/return"), new ReturnRequest("b2", "m2"), ResultWithNextResponse.class);

    assertNotNull(wrongReturn);
    assertFalse(wrongReturn.ok(), "Return should fail when requester is not current borrower");

    BookResponse book =
        rest.getForObject(url("/api/books"), BooksResponse.class).items().stream()
            .filter(b -> Objects.equals(b.id(), "b2"))
            .findFirst()
            .orElseThrow();

    assertEquals("m1", book.loanedTo(), "Loan should remain with original borrower");
  }

  @Test
  void reserveAvailableBookLoansImmediately() {
    ResultResponse reserved =
        rest.postForObject(
            url("/api/reserve"), new ReserveRequest("b3", "m2"), ResultResponse.class);

    assertNotNull(reserved);
    assertTrue(reserved.ok());

    BookResponse book =
        rest.getForObject(url("/api/books"), BooksResponse.class).items().stream()
            .filter(b -> Objects.equals(b.id(), "b3"))
            .findFirst()
            .orElseThrow();

    assertEquals("m2", book.loanedTo(), "Available book should be loaned immediately to reserver");
    assertFalse(book.reservationQueue().contains("m2"), "Reserver must not remain in queue");
  }

  @Test
  void duplicateReservationIsRejected() {
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b4", "m1"), ResultResponse.class);

    ResultResponse r1 =
        rest.postForObject(
            url("/api/reserve"), new ReserveRequest("b4", "m2"), ResultResponse.class);
    assertNotNull(r1);
    assertTrue(r1.ok());

    ResultResponse r2 =
        rest.postForObject(
            url("/api/reserve"), new ReserveRequest("b4", "m2"), ResultResponse.class);
    assertNotNull(r2);
    assertFalse(r2.ok(), "Duplicate reservation should be rejected");
  }

  @Test
  void returnHandsOffToNextEligibleReserver_andMaintainsQueue() {
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b5", "m1"), ResultResponse.class);
    rest.postForObject(url("/api/reserve"), new ReserveRequest("b5", "m2"), ResultResponse.class);
    rest.postForObject(url("/api/reserve"), new ReserveRequest("b5", "m3"), ResultResponse.class);

    ResultWithNextResponse returned =
        rest.postForObject(
            url("/api/return"), new ReturnRequest("b5", "m1"), ResultWithNextResponse.class);

    assertNotNull(returned);
    assertTrue(returned.ok());
    assertEquals("m2", returned.nextMemberId(), "Book should be handed off to head of queue");

    BookResponse book =
        rest.getForObject(url("/api/books"), BooksResponse.class).items().stream()
            .filter(b -> Objects.equals(b.id(), "b5"))
            .findFirst()
            .orElseThrow();

    assertEquals("m2", book.loanedTo(), "Book must be loaned to next eligible reserver");
    assertEquals(java.util.List.of("m3"), book.reservationQueue(), "Queue must drop the recipient");
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  @Test
  void returnSkipsMissingReserver_andHandsOffToNext() {
    // Loan book to m1
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b6", "m1"), ResultResponse.class);

    // Reserve by m2 then m3
    rest.postForObject(url("/api/reserve"), new ReserveRequest("b6", "m2"), ResultResponse.class);
    rest.postForObject(url("/api/reserve"), new ReserveRequest("b6", "m3"), ResultResponse.class);

    // Delete m2 (missing member)
    rest.exchange(
        url("/api/members"),
        HttpMethod.DELETE,
        new HttpEntity<>(new DeleteMemberRequest("m2")),
        ResultResponse.class);

    // Return by borrower -> should skip m2 and hand to m3
    ResultWithNextResponse returned =
        rest.postForObject(
            url("/api/return"), new ReturnRequest("b6", "m1"), ResultWithNextResponse.class);

    assertNotNull(returned);
    assertTrue(returned.ok());
    assertEquals("m3", returned.nextMemberId());

    BookResponse book =
        rest.getForObject(url("/api/books"), BooksResponse.class).items().stream()
            .filter(b -> Objects.equals(b.id(), "b6"))
            .findFirst()
            .orElseThrow();

    assertEquals("m3", book.loanedTo());
    assertTrue(book.reservationQueue().isEmpty());
  }

  @Test
  void returnSkipsIneligibleReserver_atBorrowLimit_andHandsOffToNext() {
    // Put m2 at max loans (5)
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b1", "m2"), ResultResponse.class);
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b2", "m2"), ResultResponse.class);
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b3", "m2"), ResultResponse.class);
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b4", "m2"), ResultResponse.class);
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b5", "m2"), ResultResponse.class);

    // Loan b6 to m1
    rest.postForObject(url("/api/borrow"), new BorrowRequest("b6", "m1"), ResultResponse.class);

    // Queue m2 then m3
    rest.postForObject(url("/api/reserve"), new ReserveRequest("b6", "m2"), ResultResponse.class);
    rest.postForObject(url("/api/reserve"), new ReserveRequest("b6", "m3"), ResultResponse.class);

    // Return by borrower -> should skip m2 (ineligible) and hand to m3
    ResultWithNextResponse returned =
        rest.postForObject(
            url("/api/return"), new ReturnRequest("b6", "m1"), ResultWithNextResponse.class);

    assertNotNull(returned);
    assertTrue(returned.ok());
    assertEquals("m3", returned.nextMemberId());

    BookResponse book =
        rest.getForObject(url("/api/books"), BooksResponse.class).items().stream()
            .filter(b -> Objects.equals(b.id(), "b6"))
            .findFirst()
            .orElseThrow();

    assertEquals("m3", book.loanedTo());
    assertTrue(book.reservationQueue().isEmpty());
  }
}
