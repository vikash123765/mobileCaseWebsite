
package com.vikash.mobileCaseBackend.service;
import com.vikash.mobileCaseBackend.model.*;
import com.vikash.mobileCaseBackend.repo.*;
import com.vikash.mobileCaseBackend.service.EmailUtility.SendMailOrderInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class OrderEntityService {

    @Autowired
    IRepoUser repoUser;

    @Autowired
    IRepoProduct repoProduct;

    @Autowired
    IRepoOrder repoOrder;

    @Autowired
    AuthService authenticationService;


    @Autowired
    CartService cartService;


    @Autowired
    SendMailOrderInfo sendMailOrderInfo;



    @Autowired
    IRepoCart repoCart;

    @Autowired
    IRepoGuestCart iRepoGuestCart;
    @Autowired
    IRepoGuestCartItem iRepoGuestCartItem;



    public List<Map<String, Object>> getOrderHistoryByUserEmail(String email, String tokenValue) {
        if (authenticationService.authenticate(email, tokenValue)) {

            // figure out the actual user  with email
            User user = repoUser.findByUserEmail(email);

            // figure out the actual orders of that user
            List<OrderEntity> orderTobeAcessed = repoOrder.findOrderByUser(user);


            if(authorizeOrderHistoryAccesser(email,orderTobeAcessed)) {


                List<Map<String, Object>> orderList = new ArrayList<>();

                for (OrderEntity order : orderTobeAcessed) {
                    Map<String, Object> orderMap = new HashMap<>();
                    orderMap.put("orderId", order.getOrderNumber());
                    orderMap.put("userName", order.getUser().getUserName());
                    orderMap.put("order placed : ",order.getSetCreatingTimeStamp());
                    //orderMap.put("delivered", order.get);
                    // orderMap.put("sent", order.());

                    // Fetch products associated with the order via repository query
                    List<Product> products = repoProduct.findProductByOrders(order);
                    List<Map<String, Object>> productDetails = new ArrayList<>();

                    for (Product product : products) {
                        Map<String, Object> productMap = new HashMap<>();
                        productMap.put("productName", product.getProductName());
                        productMap.put("productType", product.getProductType());
                        productMap.put("productPrice", product.getProductPrice());



                        productDetails.add(productMap);
                    }

                    orderMap.put("products", productDetails);
                    orderList.add(orderMap);
                }

                return orderList;
            }

            else{
                return Collections.singletonList(Collections.singletonMap("message", "Unuthorized access"));
            }

        } else {
            // Return a message indicating unauthenticated access
            return Collections.singletonList(Collections.singletonMap("message", "Unauthenticated access"));
        }

    }


    private boolean authorizeOrderHistoryAccesser(String email, List<OrderEntity> orderTobeAcessed) {
        User potentialAccesser = repoUser.findByUserEmail(email);
        for (OrderEntity order : orderTobeAcessed) {
            if (order.getUser().getUserEmail().equals(potentialAccesser.getUserEmail())) {
                return true;
            }
        }
        return false;
    }



    public String markOrderAsSent(String email, String tokenValue, Integer orderNr, Integer trackingId) {
        if (authenticationService.authenticate(email, tokenValue)) {

            OrderEntity order = repoOrder.findByOrderNumber(orderNr);

            // Check if trackingId is provided
            if (trackingId != null) {
                order.setTrackingNumber(trackingId);
            }

            if (!order.isMarkAsSent()) {
                order.setMarkAsSent(true);
                repoOrder.save(order);

                // Send email notification
                String subject = "Order Marked as Sent";
                String body = "Your order with order number " + orderNr + " has been marked as sent.";

                // Include tracking ID in the email body if available
                if (trackingId != null) {
                    body += "\nTracking ID: " + trackingId;
                }

                body += "\nThank you for shopping with us!";

                sendMailOrderInfo.sendEmail(order.getUser().getUserEmail(), subject, body, order);



                // You can also notify the admin if needed
                String adminEmail="vikash.kosaraju1234@gmail.com";
                sendMailOrderInfo.sendEmail(adminEmail, subject, body, order);

                return "Order with order number: " + orderNr + " is marked as sent";
            } else {
                return "Order already sent";
            }
        } else {
            return "Unauthenticated access!!!";
        }
    }


    public String markOrderAsDelivered(String email, String tokenValue,Integer orderNr) {
        if (authenticationService.authenticate(email, tokenValue)) {
            OrderEntity order = repoOrder.findByOrderNumber(orderNr);
            if(!order.isMarkAsDelivered()){
                order.setMarkAsDelivered(true);
                repoOrder.save(order);
                return "order with  order number : " + orderNr + "is marked as done";
            }else{
                return "order already sent";
            }
        } else {
            return "Un Authenticated access!!!";
        }

    }


    public String finalizeOrder(String email, String token) {
        if (authenticationService.authenticate(email, token)) {
            User user = repoUser.findByUserEmail(email);
            Cart cart = cartService.getCartByUser(user);
            List<CartItem> cartItems = cart.getCartItems();

            // Check if the cart has items
            if (cartItems.isEmpty()) {
                return "Cart is empty. Cannot finalize order.";
            }

            // Create and populate an OrderEntity
            OrderEntity order = new OrderEntity();
            order.setUser(user);
            order.setSetCreatingTimeStamp(LocalDateTime.now());

            // Save the order to the database first
            repoOrder.save(order);

            // Create a separate list to collect products
            List<Product> productsToUpdate = new ArrayList<>();

            // Retrieve the products from the cart items
            for (CartItem cartItem : cartItems) {
                Product orderProduct = cartItem.getProduct();

                // Set the product's orderEntity reference to the saved order
                orderProduct.getOrders().add(order);
                productsToUpdate.add(orderProduct);
            }

            // Save all products after modifying relationships
            repoProduct.saveAll(productsToUpdate);

            // Update the user's orders outside the loop
            user.getOrders().add(order);
            repoUser.save(user);

            // Optionally mark the cart as having the order placed
            cart.setOrderPlaced(true);
            repoCart.save(cart);

            // booleam check

            // Reset the user's cart after the order is finalized
            cartService.resetCart(user);

            // Send email notifications
            String userSubject = "Order Placed";
            String userBody = "Your order has been placed. Thank you for shopping with us!";
            sendMailOrderInfo.sendEmail(user.getUserEmail(), userSubject, userBody, order);

            String adminEmail = "vikash.kosaraju1234@gmail.com"; // Replace with your actual admin email
            String adminSubject = "New Order Placed";
            String adminBody = "A new order has been placed. Order Number: " + order.getOrderNumber();
            sendMailOrderInfo.sendEmail(adminEmail, adminSubject, adminBody, order);

            return "Order finalized successfully!";
        } else {
            return "Unauthorized access";
        }
    }



    public String finalizeGuestOrder( GuestOrderRequest guestOrderRequest) {
        // Create a new guest user
        User guestUser = new User();
        guestUser.setUserName(guestOrderRequest.getUserName());
        guestUser.setUserEmail(guestOrderRequest.getEmail());
        guestUser.setAddress(guestOrderRequest.getShippingAddress());
        guestUser.setPhoneNumber(guestOrderRequest.getPhoneNumber());

        // Save the guest user to the database
        User savedGuestUser = repoUser.save(guestUser);

        // Create and populate a GuestOrderEntity
        OrderEntity guestOrder = new OrderEntity();
        guestOrder.setMarkAsSent(false); // Set default values for other order-related fields
        guestOrder.setMarkAsDelivered(false);
        guestOrder.setSetCreatingTimeStamp(LocalDateTime.now());

        // Link the guest order with the guest user
        guestOrder.setUser(savedGuestUser);

        // Save the order to the database
        repoOrder.save(guestOrder);

        // Retrieve the products from the guest cart items
       // GuestCart guestCart = iRepoGuestCart.findById(guestCartId).orElse(null);
        // Generate or fetch session token internally (pseudo code, you'll need to implement this)
        String sessionToken = generateOrFetchSessionToken(savedGuestUser);
        GuestCart guestCart = iRepoGuestCart.findBySessionToken(sessionToken);
        if (guestCart != null) {
            List<GuestCartItem> guestCartItems = guestCart.getGuestCartItems();

            for (GuestCartItem guestCartItem : guestCartItems) {
                Product orderProduct = guestCartItem.getProduct();

                // Link the product with the guest order
                orderProduct.getOrders().add(guestOrder);
                // Link the product with the guest user
                orderProduct.getUsers().add(savedGuestUser);

                // Save the product to associate it with the new order
                repoProduct.save(orderProduct);
            }

            // Link the guest order with the guest user's orders
            savedGuestUser.getOrders().add(guestOrder);
            repoUser.save(savedGuestUser);

            // Clear the guest cart items
            guestCartItems.clear();
            iRepoGuestCartItem.deleteAll(guestCartItems);

            // Send email notifications
            String userSubject = "Guest Order Placed";
            String userBody = "Your guest order has been placed. Thank you for shopping with us!";
            sendMailOrderInfo.sendEmail(savedGuestUser.getUserEmail(), userSubject, userBody, guestOrder);

            String adminEmail = "admin@example.com"; // Replace with your actual admin email
            String adminSubject = "New Guest Order Placed";
            String adminBody = "A new guest order has been placed. Order Number: " + guestOrder.getOrderNumber();
            sendMailOrderInfo.sendEmail(adminEmail, adminSubject, adminBody, guestOrder);

            return "Guest order finalized successfully!";
        } else {
            return "Guest cart not found. Order finalization failed.";
        }
    }

    private String generateOrFetchSessionToken(User savedGuestUser) {
        // Assuming you have a method to get the session token from the user or some other source
        String sessionToken = generateSessionTokenForUser(savedGuestUser); // Implement this method as needed

        // Check if a guest cart with this session token already exists
        GuestCart existingGuestCart = iRepoGuestCart.findBySessionToken(sessionToken);

        if (existingGuestCart != null) {
            // If a session token already exists, return it
            return existingGuestCart.getSessionToken();
        } else {
            // Generate a new session token using UUID
            String newSessionToken = UUID.randomUUID().toString();

            // Create a new GuestCart entity
            GuestCart newGuestCart = new GuestCart();
            newGuestCart.setSessionToken(newSessionToken);
            newGuestCart.setOrderPlaced(false); // Assuming this is the default state

            // Link the guest user with the guest cart
            newGuestCart.getUsers().add(savedGuestUser);  // Ensure the method exists to add user to guest cart

            // Save the GuestCart to associate the session token with the guest user
            iRepoGuestCart.save(newGuestCart);

            return newSessionToken;
        }
    }

    private String generateSessionTokenForUser(User savedGuestUser) {
        // Generate a unique session token using UUID
        return UUID.randomUUID().toString();
    }
    
       

/*    private boolean checkPaymentStatus() {

    }*/
}


