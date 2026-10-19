
package com.ctu.service;

import com.ctu.exception.PaymentFailedException;
import com.ctu.exception.RoomNotAvailableException;
import com.ctu.model.BookingReceipt;
import com.ctu.model.Room;
import com.ctu.repository.RoomRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private PaymentGateway paymentGateway;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(
                roomRepository, paymentGateway);
    }

    // 1. Dat phong thanh cong
    @Test
    void testBookRoom_Success() {
        Room room = new Room("R101", 500.0, true);

        when(roomRepository.findById("R101"))
                .thenReturn(Optional.of(room));
        when(paymentGateway.charge("123456", 1000.0))
                .thenReturn(true);

        BookingReceipt receipt =
                bookingService.bookRoom("R101", 2, "123456");

        assertNotNull(receipt);
        assertNotNull(receipt.getBookingId());
        assertEquals("R101", receipt.getRoomId());
        assertEquals(1000.0, receipt.getTotalAmount(), 0.001);
        assertFalse(room.isAvailable());

        verify(roomRepository, times(1)).findById("R101");
        verify(paymentGateway, times(1))
                .charge("123456", 1000.0);
        verify(roomRepository, times(1)).save(room);
        verifyNoMoreInteractions(roomRepository, paymentGateway);
    }

    // 2. Khong tim thay phong
    @Test
    void testBookRoom_RoomNotFound_ThrowsException() {
        when(roomRepository.findById("R999"))
                .thenReturn(Optional.empty());

        assertThrows(RoomNotAvailableException.class,
                () -> bookingService.bookRoom(
                        "R999", 2, "123456"));

        verify(roomRepository, times(1)).findById("R999");
        verify(roomRepository, never()).save(any(Room.class));
        verifyNoInteractions(paymentGateway);
    }

    // 3. Phong da duoc dat truoc
    @Test
    void testBookRoom_RoomAlreadyBooked_ThrowsException() {
        Room room = new Room("R101", 500.0, false);

        when(roomRepository.findById("R101"))
                .thenReturn(Optional.of(room));

        assertThrows(RoomNotAvailableException.class,
                () -> bookingService.bookRoom(
                        "R101", 2, "123456"));

        verify(roomRepository, times(1)).findById("R101");
        verify(roomRepository, never()).save(any(Room.class));
        verifyNoInteractions(paymentGateway);
    }

    // 4. Thanh toan that bai
    @Test
    void testBookRoom_PaymentFailed_ThrowsException() {
        Room room = new Room("R101", 500.0, true);

        when(roomRepository.findById("R101"))
                .thenReturn(Optional.of(room));
        when(paymentGateway.charge("123456", 1000.0))
                .thenReturn(false);

        assertThrows(PaymentFailedException.class,
                () -> bookingService.bookRoom(
                        "R101", 2, "123456"));

        assertTrue(room.isAvailable());

        verify(roomRepository, times(1)).findById("R101");
        verify(paymentGateway, times(1))
                .charge("123456", 1000.0);
        verify(roomRepository, never()).save(any(Room.class));
    }

    // 5. Nights <= 0
    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void testBookRoom_InvalidNights_ThrowsException(int nights) {
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.bookRoom(
                        "R101", nights, "123456"));

        verifyNoInteractions(roomRepository, paymentGateway);
    }

    // 6. Credit card null, rong hoac chi co khoang trang
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void testBookRoom_NullOrEmptyCreditCard_ThrowsException(
            String creditCard) {

        assertThrows(IllegalArgumentException.class,
                () -> bookingService.bookRoom(
                        "R101", 2, creditCard));

        verifyNoInteractions(roomRepository, paymentGateway);
    }
}
