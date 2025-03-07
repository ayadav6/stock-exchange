# Real-time Stock Trading Engine

Name:Ashish Yadav


# Overview: 

Implemented a real time stock trading engine for matching stock buys with stock sell for 1,024 tickers, using lock-free data structures for concurrency safety. Added a simulation wrapper for creating 1000 orders.

# Code Structure

- Order: Represents an order (BUY/SELL) with atomic quantity and filled status.
- LockFreeQueue: Thread-safe queue using AtomicReference for lock-free enqueueing.
- OrderBook: Manages BUY/SELL queues for a ticker and matches orders.
- StockExchange: Maps tickers to 1,024 OrderBook instances.
- SimulationWrapper: Simulates 1000 orders for BUY/SELL.
