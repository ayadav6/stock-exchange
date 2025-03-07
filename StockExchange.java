import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class StockExchange {
    private final OrderBook[] orderBooks = new OrderBook[1024];

    public StockExchange() {
        for (int i = 0; i < orderBooks.length; i++) {
            orderBooks[i] = new OrderBook();
        }
    }

    public void addOrder(Order.Type type, String ticker, int quantity, int price) {
        int index = parseTickerToIndex(ticker);
        Order order = new Order(type, ticker, quantity, price);
        orderBooks[index].addOrder(order);
    }

    public void matchOrder(String ticker) {
        int index = parseTickerToIndex(ticker);
        orderBooks[index].matchOrders();
    }

    private int parseTickerToIndex(String ticker) {
        return Integer.parseInt(ticker) % 1024;
    }

    public static void main(String[] args) {
        StockExchange exchange = new StockExchange();
        // exchange.addOrder(Order.Type.BUY, "0", 100, 10);
        // exchange.addOrder(Order.Type.SELL, "0", 100, 10);
        // exchange.matchOrder("0");
    
        // // Test Case 2: Partial fill (Buy 150@20 ↔ Sell 100@15)
        // exchange.addOrder(Order.Type.BUY, "1", 150, 20);
        // exchange.addOrder(Order.Type.SELL, "1", 100, 15);
        // exchange.matchOrder("1");
    
        // // Test Case 3: No match (Buy 50@5 vs Sell 50@10)
        // exchange.addOrder(Order.Type.BUY, "2", 50, 5);
        // exchange.addOrder(Order.Type.SELL, "2", 50, 10);
        // exchange.matchOrder("2");
       
       
         SimulationWrapper simulator = new SimulationWrapper(exchange);
         simulator.simulateOrders();
    }
}


class Order {
    public enum Type { BUY, SELL }

    private final Type type;
    private final String ticker;
    private final AtomicInteger quantity;
    private final int price;
    private final AtomicBoolean isFilled = new AtomicBoolean(false);

    public Order(Type type, String ticker, int quantity, int price) {
        this.type = type;
        this.ticker = ticker;
        this.quantity = new AtomicInteger(quantity);
        this.price = price;
    }

    public Type getType() { return type; }
    public String getTicker() { return ticker; }
    public AtomicInteger getQuantity() { return quantity; }
    public int getPrice() { return price; }
    public AtomicBoolean getIsFilled() { return isFilled; }



    @Override
    public String toString() {
        return type + " " + ticker + " | Qty: " + quantity.get() + " | Price: $" + price;
    }
}

class OrderNode {
    final Order order;
    final AtomicReference<OrderNode> next = new AtomicReference<>();

    OrderNode(Order order) {
        this.order = order;
    }
}

class LockFreeQueue {
    private final OrderNode head = new OrderNode(null);
    private final AtomicReference<OrderNode> tail = new AtomicReference<>(head);

    public void enqueue(Order order) {
        OrderNode node = new OrderNode(order);
        while (true) {
            OrderNode currentTail = tail.get();
            OrderNode next = currentTail.next.get();
            if (currentTail == tail.get()) {
                if (next == null) {
                    if (currentTail.next.compareAndSet(null, node)) {
                        tail.compareAndSet(currentTail, node);
                        return;
                    }
                } else {
                    tail.compareAndSet(currentTail, next);
                }
            }
        }
    }

    public OrderNode getHead() { return head; }
}

class OrderBook {
    private final LockFreeQueue buyOrders = new LockFreeQueue();
    private final LockFreeQueue sellOrders = new LockFreeQueue();

    public void addOrder(Order order) {
        if (order.getType() == Order.Type.BUY) {
            buyOrders.enqueue(order);
        } else {
            sellOrders.enqueue(order);
        }
    }

    public void matchOrders() {
        int minSellPrice = findMinSellPrice();
        if (minSellPrice == Integer.MAX_VALUE) return;

        processBuyOrdersAgainstMinPrice(minSellPrice);
    }

    private int findMinSellPrice() {
        int minSellPrice = Integer.MAX_VALUE;
        OrderNode current = sellOrders.getHead().next.get();
        while (current != null) {
            Order sellOrder = current.order;
            if (!sellOrder.getIsFilled().get()) {
                minSellPrice = Math.min(minSellPrice, sellOrder.getPrice());
            }
            current = current.next.get();
        }
        return minSellPrice;
    }

    private void processBuyOrdersAgainstMinPrice(int minSellPrice) {
        OrderNode buyNode = buyOrders.getHead().next.get();
        while (buyNode != null) {
            Order buyOrder = buyNode.order;
            if (!buyOrder.getIsFilled().get() && buyOrder.getPrice() >= minSellPrice) {
                OrderNode sellNode = sellOrders.getHead().next.get();
                while (sellNode != null) {
                    Order sellOrder = sellNode.order;
                    if (!sellOrder.getIsFilled().get() && sellOrder.getPrice() == minSellPrice) {
                        matchOrder(buyOrder, sellOrder);
                        if (buyOrder.getIsFilled().get()) break;
                    }
                    sellNode = sellNode.next.get();
                }
            }
            buyNode = buyNode.next.get();
        }
    }

    private void matchOrder(Order buyOrder, Order sellOrder) {
        while (true) {
            int buyQty = buyOrder.getQuantity().get();
            if (buyQty <= 0) {
                buyOrder.getIsFilled().set(true);
                break;
            }
            int sellQty = sellOrder.getQuantity().get();
            if (sellQty <= 0) {
                sellOrder.getIsFilled().set(true);
                break;
            }
            int matchedQty = Math.min(buyQty, sellQty);
            boolean buyUpdated = buyOrder.getQuantity().compareAndSet(buyQty, buyQty - matchedQty);
            boolean sellUpdated = sellOrder.getQuantity().compareAndSet(sellQty, sellQty - matchedQty);
            if (buyUpdated && sellUpdated) {
                if (buyQty - matchedQty == 0) buyOrder.getIsFilled().set(true);
                if (sellQty - matchedQty == 0) sellOrder.getIsFilled().set(true);
                // FIX: Log original quantities and matched amount
                System.out.println("[MATCHED] " + buyOrder.getType() + " " + buyOrder.getTicker() 
                    + " | Original Qty: " + buyQty + " | Price: $" + buyOrder.getPrice() 
                    + " ↔ " + sellOrder.getType() + " " + sellOrder.getTicker() 
                    + " | Original Qty: " + sellQty + " | Price: $" + sellOrder.getPrice());
                System.out.println("  ↪ Matched Qty: " + matchedQty);
                System.out.println("  ↪ Remaining Buy Qty: " + (buyQty - matchedQty));
                System.out.println("  ↪ Remaining Sell Qty: " + (sellQty - matchedQty));
                break;
            }
        }
    }

    
}


class SimulationWrapper {
    private final StockExchange exchange;
    private final Random random = new Random();

    public SimulationWrapper(StockExchange exchange) {
        this.exchange = exchange;
    }

    public void simulateOrders() {
        for (int i = 0; i < 1000; i++) {
            Order.Type type = random.nextBoolean() ? Order.Type.BUY : Order.Type.SELL;
            String ticker = String.valueOf(random.nextInt(1024));
            int quantity = random.nextInt(100) + 1;
            int price = random.nextInt(1000) + 1;
            exchange.addOrder(type, ticker, quantity, price);
            System.out.println("[PLACED] " + type + " " + ticker + " | Qty: " + quantity + " | Price: $" + price);
            exchange.matchOrder(ticker);
        }
    }
}