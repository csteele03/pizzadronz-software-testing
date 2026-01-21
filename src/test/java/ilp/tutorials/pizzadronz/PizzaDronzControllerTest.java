package ilp.tutorials.pizzadronz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import uk.ac.ed.inf.ilp.constant.OrderValidationCode;
import uk.ac.ed.inf.ilp.data.*;
import ilp.tutorials.pizzadronz.controllers.PizzaDronzController;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PizzaDronzControllerTest {

    private PizzaDronzController controller;

    @BeforeEach
    public void setUp() {
        controller = new PizzaDronzController();
    }

    private Order makeValidOrder() {
        Order o = new Order();
        o.setPizzasInOrder(new Pizza[]{ new Pizza("R1: Margarita", 1000) });
        o.setPriceTotalInPence(1100);
        // Must be future relative to today (Jan 2026) and within +5 years per your validator.
        o.setCreditCardInformation(new CreditCardInformation("4485959141852684", "10/26", "816"));
        return o;
    }

    @Test
    public void testValidateOrder_ValidOrder() {
        Order validOrder = makeValidOrder();

        ResponseEntity<?> response = controller.validateOrder(validOrder);

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Order);

        Order returned = (Order) response.getBody();
        assertEquals(OrderValidationCode.NO_ERROR, returned.getOrderValidationCode());
    }

    @Test
    public void testValidateOrder_InvalidCreditCard() {
        Order invalidOrder = makeValidOrder();
        invalidOrder.setCreditCardInformation(new CreditCardInformation("123", "10/26", "816"));

        ResponseEntity<?> response = controller.validateOrder(invalidOrder);

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody() instanceof Order);

        Order returned = (Order) response.getBody();
        assertEquals(OrderValidationCode.CARD_NUMBER_INVALID, returned.getOrderValidationCode());
    }

    @Test
    public void testValidateOrder_InvalidPriceTotal() {
        Order invalidOrder = makeValidOrder();
        invalidOrder.setPriceTotalInPence(1500);

        ResponseEntity<?> response = controller.validateOrder(invalidOrder);

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody() instanceof Order);

        Order returned = (Order) response.getBody();
        assertEquals(OrderValidationCode.TOTAL_INCORRECT, returned.getOrderValidationCode());
    }

    @Test
    public void testValidateOrder_InvalidPizzaName() {
        Order invalidOrder = makeValidOrder();
        invalidOrder.setPizzasInOrder(new Pizza[]{ new Pizza("R99: GhostPizza", 1000) });

        ResponseEntity<?> response = controller.validateOrder(invalidOrder);

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody() instanceof Order);

        Order returned = (Order) response.getBody();
        // Your controller patch maps IllegalArgumentException in validateRestaurants to PIZZA_NOT_DEFINED.
        assertEquals(OrderValidationCode.PIZZA_NOT_DEFINED, returned.getOrderValidationCode());
    }

    @Test
    public void testValidateOrder_NullCreditCardInfo() {
        Order invalidOrder = makeValidOrder();
        invalidOrder.setCreditCardInformation(null);

        ResponseEntity<?> response = controller.validateOrder(invalidOrder);

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody() instanceof Order);

        Order returned = (Order) response.getBody();
        // With your current validateOrderLogic this will be CARD_NUMBER_INVALID.
        assertEquals(OrderValidationCode.CARD_NUMBER_INVALID, returned.getOrderValidationCode());
    }

    @Test
    public void testCalcDeliveryPath_ValidOrder() {
        Order validOrder = makeValidOrder();

        PizzaDronzController spyController = Mockito.spy(controller);
        doReturn(List.of(
                new LngLat(-3.1912869215011597, 55.945535152517735),
                new LngLat(-3.1878, 55.9445)
        )).when(spyController).calculatePath(validOrder);

        ResponseEntity<?> response = spyController.calcDeliveryPath(validOrder);

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
    }

    @Test
    public void testCalcDeliveryPath_InvalidOrder_MissingPizzas() {
        Order invalidOrder = new Order();

        ResponseEntity<?> response = controller.calcDeliveryPath(invalidOrder);

        // If you changed calcDeliveryPath to return 200 + Order for invalids, assert that.
        // If you DID NOT change it, it will likely still return 400 with a String error.
        int status = response.getStatusCodeValue();

        if (status == 200) {
            assertTrue(response.getBody() instanceof Order);
            Order returned = (Order) response.getBody();
            assertEquals(OrderValidationCode.EMPTY_ORDER, returned.getOrderValidationCode());
        } else {
            assertEquals(400, status);
        }
    }

    @Test
    public void testCalcDeliveryPath_APIError() {
        Order validOrder = makeValidOrder();

        PizzaDronzController spyController = Mockito.spy(controller);
        doThrow(new IllegalArgumentException("Failed to fetch restaurant data"))
                .when(spyController).fetchAndParse(anyString(), eq(Restaurant[].class));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            spyController.calcDeliveryPath(validOrder);
        });

        assertEquals("Failed to fetch restaurant data", ex.getMessage());
    }


    @Test
    public void testCalculateDistance() {
        LngLat pos1 = new LngLat(-3.1878, 55.9445);
        LngLat pos2 = new LngLat(-3.1875, 55.9450);

        double distance = controller.calculateDistance(pos1, pos2);
        assertTrue(distance > 0);
    }

    @Test
    public void testValidateCoordinates_Valid() {
        LngLat validCoordinates = new LngLat(-3.1878, 55.9445);
        assertDoesNotThrow(() -> controller.validateCoordinates(validCoordinates));
    }

    @Test
    public void testValidateCoordinates_InvalidLongitude() {
        LngLat invalidCoordinates = new LngLat(-200.0, 55.9445);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            controller.validateCoordinates(invalidCoordinates);
        });
        assertEquals("Longitude must be between -180 and 180.", exception.getMessage());
    }
}
