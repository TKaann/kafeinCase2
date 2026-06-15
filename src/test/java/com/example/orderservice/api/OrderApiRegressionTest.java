package com.example.orderservice.api;

import com.example.orderservice.api.controller.OrderController;
import com.example.orderservice.api.controller.ProductController;
import com.example.orderservice.api.mapper.OrderMapper;
import com.example.orderservice.api.mapper.ProductMapper;
import com.example.orderservice.config.WebConfig;
import com.example.orderservice.domain.enums.PaymentMethod;
import com.example.orderservice.exception.InsufficientStockException;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.exception.PaymentFailedException;
import com.example.orderservice.exception.ProductNotFoundException;
import com.example.orderservice.service.BulkOrderService;
import com.example.orderservice.service.OrderService;
import com.example.orderservice.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Automated regression for the HTTP error matrix (Faz 4). Runs the web layer in isolation with
 * mocked services and asserts both the status code and the {@code ApiError} body for each case.
 */
@WebMvcTest(controllers = {OrderController.class, ProductController.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebConfig.class))
@Import({OrderMapper.class, ProductMapper.class})
class OrderApiRegressionTest {

    private static final String VALID_ORDER = """
            {"customerId":1,"paymentMethod":"CREDIT_CARD","items":[{"productId":1,"quantity":2}]}""";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;
    @MockBean
    private BulkOrderService bulkOrderService;
    @MockBean
    private ProductService productService;

    @Test
    void getMissingProduct_returns404() throws Exception {
        when(productService.getById(999L)).thenThrow(new ProductNotFoundException(999L));

        mockMvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message", containsString("Product not found")))
                .andExpect(jsonPath("$.path").value("/api/products/999"));
    }

    @Test
    void getMissingOrder_returns404() throws Exception {
        when(orderService.getOrderDetail(999L)).thenThrow(new OrderNotFoundException(999L));

        mockMvc.perform(get("/api/orders/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Order not found")));
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("Malformed")));
    }

    @Test
    void invalidEnumValue_returns400() throws Exception {
        String body = """
                {"customerId":1,"paymentMethod":"BITCOIN","items":[{"productId":1,"quantity":1}]}""";

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Malformed")));
    }

    @Test
    void typeMismatchPathVariable_returns400() throws Exception {
        mockMvc.perform(get("/api/products/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid value 'abc'")));
    }

    @Test
    void fieldValidationError_returns400WithFieldMessage() throws Exception {
        String body = """
                {"customerId":1,"paymentMethod":"CREDIT_CARD","items":[{"productId":1,"quantity":0}]}""";

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("quantity must be at least 1")));
    }

    @Test
    void insufficientStock_returns409() throws Exception {
        when(orderService.createOrder(any()))
                .thenThrow(new InsufficientStockException(8L, "Limited Edition Pen", 6, 100));

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("Insufficient stock")));
    }

    @Test
    void paymentFailure_returns422() throws Exception {
        when(orderService.createOrder(any()))
                .thenThrow(new PaymentFailedException(PaymentMethod.CREDIT_CARD, "gateway unavailable"));

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message", containsString("Payment failed")));
    }
}
