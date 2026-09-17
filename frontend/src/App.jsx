import React, { useState, useEffect } from 'react';
import './App.css';

const API_BASE_URL = 'http://localhost:8080';
const LOW_STOCK_THRESHOLD = 5;

export default function App() {
  const [inventory, setInventory] = useState([]);
  const [cart, setCart] = useState([]);
  const [loading, setLoading] = useState(false);
  const [cancellingOrderId, setCancellingOrderId] = useState(null);
  const [orders, setOrders] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [lastResult, setLastResult] = useState(null);
  const [toast, setToast] = useState(null);

  // Initial load
  useEffect(() => {
    refreshAll();
    // Poll notifications periodically every 8 seconds for live activity
    const interval = setInterval(() => {
      fetchNotifications();
    }, 8000);
    return () => clearInterval(interval);
  }, []);

  const showToast = (message, type = 'info') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 4000);
  };

  const fetchInventory = async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/api/inventory`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      setInventory(data);
    } catch (err) {
      console.error('Inventory fetch error:', err);
    }
  };

  const fetchOrders = async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/api/orders`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      setOrders(data);
    } catch (err) {
      console.error('Orders fetch error:', err);
    }
  };

  const fetchNotifications = async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/api/notifications`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      setNotifications(data);
    } catch (err) {
      console.error('Notifications fetch error:', err);
    }
  };

  const refreshAll = async () => {
    await Promise.all([fetchInventory(), fetchOrders(), fetchNotifications()]);
  };

  // Cart operations
  const addToCart = (product) => {
    setCart(prev => {
      const existing = prev.find(item => item.productId === product.productId);
      if (existing) {
        return prev.map(item =>
          item.productId === product.productId
            ? { ...item, quantity: item.quantity + 1 }
            : item
        );
      }
      return [...prev, { productId: product.productId, name: product.name, quantity: 1 }];
    });
    showToast(`Added ${product.name} to cart`, 'success');
  };

  const updateCartQuantity = (productId, newQty) => {
    const qty = Math.max(1, parseInt(newQty, 10) || 1);
    setCart(prev =>
      prev.map(item =>
        item.productId === productId ? { ...item, quantity: qty } : item
      )
    );
  };

  const removeFromCart = (productId) => {
    setCart(prev => prev.filter(item => item.productId !== productId));
  };

  const clearCart = () => {
    setCart([]);
  };

  const loadPreset = (presetType) => {
    if (presetType === 'success') {
      setCart([
        { productId: 'P100', name: 'Wireless Mouse', quantity: 2 },
        { productId: 'P200', name: 'Mechanical Keyboard', quantity: 1 }
      ]);
      showToast('Loaded preset: Valid Multi-Item Order (2x P100, 1x P200)', 'info');
    } else if (presetType === 'reject') {
      setCart([
        { productId: 'P100', name: 'Wireless Mouse', quantity: 2 },
        { productId: 'P200', name: 'Mechanical Keyboard', quantity: 15 } // stock is 10
      ]);
      showToast('Loaded preset: Exceeds Stock (15x P200 exceeds 10) - Tests Rollback', 'warning');
    } else if (presetType === 'lowstock') {
      // Find P100 or P200 to drop below 5
      const p100 = inventory.find(i => i.productId === 'P100');
      const qty = p100 ? Math.max(1, p100.stock - 3) : 22;
      setCart([
        { productId: 'P100', name: 'Wireless Mouse', quantity: qty }
      ]);
      showToast(`Loaded preset: Drops P100 stock to 3 (< 5) to trigger Low-Stock Alert!`, 'info');
    }
  };

  // Submit Order (Transactional Multi-Item)
  const handleSubmitOrder = async (e) => {
    e.preventDefault();
    if (cart.length === 0) {
      showToast('Cart is empty. Add at least one item.', 'warning');
      return;
    }

    setLoading(true);
    try {
      const payload = {
        items: cart.map(item => ({
          productId: item.productId,
          quantity: item.quantity
        }))
      };

      const res = await fetch(`${API_BASE_URL}/api/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (!res.ok) {
        const errText = await res.text();
        throw new Error(errText || `Server returned ${res.status}`);
      }

      const data = await res.json();
      setLastResult(data);

      if (data.status === 'CONFIRMED') {
        showToast(`Order #${data.orderId} CONFIRMED! All items reserved.`, 'success');
        setCart([]); // Clear cart upon successful reservation
      } else {
        showToast(`Order REJECTED: ${data.reason}`, 'error');
      }

      await refreshAll();
    } catch (err) {
      console.error('Order placement error:', err);
      showToast(err.message || 'Failed to submit order', 'error');
      setLastResult({
        status: 'REJECTED',
        reason: err.message || 'Network error occurred',
        items: cart.map(c => ({ productId: c.productId, quantity: c.quantity, outcome: 'ERROR' }))
      });
    } finally {
      setLoading(false);
    }
  };

  // Cancel Order & Restock
  const handleCancelOrder = async (orderId) => {
    if (!window.confirm(`Are you sure you want to cancel Order #${orderId} and return all items to stock?`)) {
      return;
    }

    setCancellingOrderId(orderId);
    try {
      const res = await fetch(`${API_BASE_URL}/api/orders/${orderId}/cancel`, {
        method: 'POST'
      });

      if (!res.ok) {
        if (res.status === 404) {
          throw new Error(`Order #${orderId} does not exist (404)`);
        } else if (res.status === 409) {
          throw new Error(`Order #${orderId} is already CANCELLED (409)`);
        } else {
          const text = await res.text();
          throw new Error(text || `HTTP ${res.status}`);
        }
      }

      const data = await res.json();
      showToast(`Order #${orderId} CANCELLED. All line items restocked to inventory.`, 'success');
      await refreshAll();
    } catch (err) {
      console.error('Cancellation error:', err);
      showToast(err.message || 'Failed to cancel order', 'error');
    } finally {
      setCancellingOrderId(null);
    }
  };

  return (
    <div className="app-container">
      {/* Toast Notification */}
      {toast && (
        <div className={`toast-notification ${toast.type}`}>
          <span>{toast.message}</span>
        </div>
      )}

      {/* Header */}
      <header className="app-header">
        <div>
          <div className="brand-badge">
            <span>Modular Monolith</span>
            <span>•</span>
            <span>Pescante</span>
            <span>•</span>
            <span>Lab 2</span>
          </div>
          <h1 className="brand-title">Order &amp; Inventory System</h1>
          <p className="brand-subtitle">
            Multi-Item Orders • All-or-Nothing Rollback • In-Monolith Domain Events • Low-Stock Alerts
          </p>
        </div>

        <div className="header-badges">
          <div className="integration-badge">
            <span className="dot live"></span>
            <span>Spring Boot 8080 (REST)</span>
          </div>
          <div className="integration-badge">
            <span className="dot db"></span>
            <span>Supabase PostgreSQL</span>
          </div>
          <div className="integration-badge">
            <span className="dot event"></span>
            <span>Spring EventPublisher</span>
          </div>
          <button onClick={refreshAll} className="refresh-main-btn" title="Refresh all data">
            ↻ Refresh All
          </button>
        </div>
      </header>

      {/* SECTION 1: Product Catalog & Multi-Item Cart */}
      <section className="catalog-and-cart-section">
        {/* Left: Product Catalog */}
        <div className="glass-panel catalog-panel">
          <div className="card-header">
            <div>
              <div className="card-title">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
                  <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
                  <line x1="12" y1="22.08" x2="12" y2="12"/>
                </svg>
                Product Catalog
              </div>
              <div className="card-desc">Click &quot;Add to Cart&quot; to build a multi-item order</div>
            </div>
          </div>

          <div className="card-body">
            <div className="product-catalog-list">
              {inventory.map(item => {
                const isOutOfStock = item.stock <= 0;
                const isLowStock = item.stock > 0 && item.stock < LOW_STOCK_THRESHOLD;

                return (
                  <div
                    key={item.productId}
                    className={`catalog-item-card ${isOutOfStock ? 'card-out' : isLowStock ? 'card-low' : ''}`}
                  >
                    <div className="catalog-item-main">
                      <div className="item-id-tag">{item.productId}</div>
                      <div className="item-name">{item.name}</div>
                      <div className="item-stock-indicator">
                        <span className={`stock-badge ${isOutOfStock ? 'badge-out' : isLowStock ? 'badge-low' : 'badge-ok'}`}>
                          {isOutOfStock ? '0 (Out of Stock)' : isLowStock ? `${item.stock} in stock (Low Stock!)` : `${item.stock} in stock`}
                        </span>
                      </div>
                    </div>

                    <button
                      type="button"
                      className="add-to-cart-btn"
                      onClick={() => addToCart(item)}
                      title={`Add ${item.name} to multi-item cart`}
                    >
                      + Add to Cart
                    </button>
                  </div>
                );
              })}
            </div>

            {/* Quick Test Presets */}
            <div className="presets-container">
              <div className="presets-label">⚡ Fast Scenario Presets:</div>
              <div className="preset-buttons-row">
                <button
                  type="button"
                  className="preset-btn success"
                  onClick={() => loadPreset('success')}
                  title="2x P100, 1x P200 (both in stock)"
                >
                  ✓ Valid Order Preset
                </button>
                <button
                  type="button"
                  className="preset-btn reject"
                  onClick={() => loadPreset('reject')}
                  title="1x P100, 15x P200 (P200 stock is only 10 -> Triggers rollback!)"
                >
                  ✕ Exceeds Stock Preset (Rollback)
                </button>
                <button
                  type="button"
                  className="preset-btn lowstock"
                  onClick={() => loadPreset('lowstock')}
                  title="Drops P100 stock to 3 (< 5 threshold) to trigger LowStock event"
                >
                  ⚠ Low-Stock Alert Preset
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Right: Multi-Item Cart */}
        <div className="glass-panel cart-panel">
          <div className="card-header">
            <div>
              <div className="card-title">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="8" cy="21" r="1"/><circle cx="19" cy="21" r="1"/>
                  <path d="M2.05 2.05h2l2.66 12.42a2 2 0 0 0 2 1.58h9.78a2 2 0 0 0 1.95-1.57l1.65-7.43H5.12"/>
                </svg>
                Active Order Cart
                {cart.length > 0 && <span className="cart-counter">{cart.length} item(s)</span>}
              </div>
              <div className="card-desc">HTTP POST /api/orders (All-or-nothing transactional reservation)</div>
            </div>

            {cart.length > 0 && (
              <button type="button" onClick={clearCart} className="clear-cart-btn">
                Clear Cart
              </button>
            )}
          </div>

          <div className="card-body">
            {cart.length === 0 ? (
              <div className="empty-cart-state">
                <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" style={{ opacity: 0.35 }}>
                  <circle cx="8" cy="21" r="1"/><circle cx="19" cy="21" r="1"/>
                  <path d="M2.05 2.05h2l2.66 12.42a2 2 0 0 0 2 1.58h9.78a2 2 0 0 0 1.95-1.57l1.65-7.43H5.12"/>
                </svg>
                <div style={{ fontWeight: 600, color: 'var(--text-secondary)', marginTop: '0.5rem' }}>Your Cart is Empty</div>
                <div style={{ fontSize: '0.825rem', color: 'var(--text-muted)' }}>
                  Click &quot;+ Add to Cart&quot; on products from the catalog or click a preset to get started.
                </div>
              </div>
            ) : (
              <form onSubmit={handleSubmitOrder}>
                <div className="cart-items-list">
                  {cart.map(item => {
                    const matchedInv = inventory.find(i => i.productId === item.productId);
                    const stock = matchedInv ? matchedInv.stock : 0;
                    const exceeds = item.quantity > stock;

                    return (
                      <div key={item.productId} className={`cart-item-row ${exceeds ? 'row-exceeds' : ''}`}>
                        <div className="cart-item-desc">
                          <div className="cart-item-title">
                            <strong>{item.productId}</strong> — {item.name}
                          </div>
                          <div className="cart-item-stock-hint">
                            Available: <strong>{stock}</strong>
                            {exceeds && (
                              <span className="stock-warning-tag">
                                ⚠ Exceeds current stock ({stock})!
                              </span>
                            )}
                          </div>
                        </div>

                        <div className="cart-stepper">
                          <button
                            type="button"
                            className="cart-step-btn"
                            onClick={() => updateCartQuantity(item.productId, item.quantity - 1)}
                            disabled={item.quantity <= 1 || loading}
                          >
                            −
                          </button>
                          <input
                            type="number"
                            min="1"
                            value={item.quantity}
                            onChange={(e) => updateCartQuantity(item.productId, e.target.value)}
                            className="cart-qty-input"
                            disabled={loading}
                          />
                          <button
                            type="button"
                            className="cart-step-btn"
                            onClick={() => updateCartQuantity(item.productId, item.quantity + 1)}
                            disabled={loading}
                          >
                            +
                          </button>
                        </div>

                        <button
                          type="button"
                          className="cart-remove-btn"
                          onClick={() => removeFromCart(item.productId)}
                          disabled={loading}
                          title="Remove item"
                        >
                          ✕
                        </button>
                      </div>
                    );
                  })}
                </div>

                <div className="cart-summary-footer">
                  <div className="cart-total-line">
                    <span>Total Line Items:</span>
                    <strong>{cart.length} product(s)</strong>
                  </div>
                  <div className="cart-total-line">
                    <span>Total Units:</span>
                    <strong>{cart.reduce((acc, i) => acc + i.quantity, 0)} unit(s)</strong>
                  </div>
                </div>

                <button
                  type="submit"
                  id="submit-multi-order-btn"
                  className="submit-btn"
                  disabled={loading || cart.length === 0}
                >
                  {loading ? (
                    <span>Validating Stock &amp; Reserving Line Items...</span>
                  ) : (
                    <>
                      <span>Submit Multi-Item Order (REST POST)</span>
                      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                        <line x1="5" y1="12" x2="19" y2="12"></line>
                        <polyline points="12 5 19 12 12 19"></polyline>
                      </svg>
                    </>
                  )}
                </button>
              </form>
            )}
          </div>
        </div>
      </section>

      {/* SECTION 2: Order Result Showcase */}
      {lastResult && (
        <section className="glass-panel result-showcase-panel animate-pop-in" style={{ marginBottom: '2.5rem' }}>
          <div className="card-header">
            <div className="card-title">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                <polyline points="22 4 12 14.01 9 11.01"/>
              </svg>
              Order Submission Result
            </div>
            <span className="card-desc">Response from OrderService</span>
          </div>

          <div className="card-body">
            <div className={`result-container ${lastResult.status.toLowerCase()}`}>
              <div className="result-header-row">
                <div className={`status-badge-lg ${lastResult.status.toLowerCase()}`}>
                  {lastResult.status === 'CONFIRMED' ? '✓ CONFIRMED' : '✕ REJECTED'}
                </div>
                {lastResult.orderId && (
                  <div className="order-id-pill">Order #{lastResult.orderId}</div>
                )}
              </div>

              <div className="result-reason">{lastResult.reason}</div>

              {/* Multi-Item Outcomes List */}
              {lastResult.items && lastResult.items.length > 0 && (
                <div className="result-items-box">
                  <div className="result-items-title">Itemized Line-Item Outcomes:</div>
                  <div className="result-items-grid">
                    {lastResult.items.map((it, idx) => (
                      <div key={idx} className={`outcome-item-card outcome-${it.outcome ? it.outcome.toLowerCase() : 'unknown'}`}>
                        <div className="outcome-item-header">
                          <strong>{it.productId}</strong>
                          {it.quantity && <span>Qty: {it.quantity}</span>}
                        </div>
                        <div className="outcome-badge">
                          {it.outcome === 'RESERVED' && '✓ RESERVED'}
                          {it.outcome === 'EXCEEDS_STOCK' && '✕ EXCEEDS STOCK'}
                          {it.outcome === 'ROLLBACK_UNFULFILLED' && '⚠ ROLLBACK (UNFULFILLED)'}
                          {it.outcome === 'PRODUCT_NOT_FOUND' && '✕ NOT FOUND'}
                          {it.outcome !== 'RESERVED' && it.outcome !== 'EXCEEDS_STOCK' && it.outcome !== 'ROLLBACK_UNFULFILLED' && it.outcome !== 'PRODUCT_NOT_FOUND' && (it.outcome || 'EVALUATED')}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        </section>
      )}

      {/* SECTION 3: Live Inventory Table with Low-Stock Highlighting */}
      <section className="glass-panel" style={{ marginBottom: '2.5rem' }}>
        <div className="card-header">
          <div>
            <div className="card-title">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/>
                <line x1="8" y1="21" x2="16" y2="21"/>
                <line x1="12" y1="17" x2="12" y2="21"/>
              </svg>
              Live Inventory Table (Auto-Refreshes on Order &amp; Cancel)
            </div>
            <div className="card-desc">GET /api/inventory • Rows highlighted when stock is below threshold ({LOW_STOCK_THRESHOLD})</div>
          </div>
          <button onClick={fetchInventory} className="chip-btn">↻ Refresh Stock</button>
        </div>

        <div style={{ overflowX: 'auto' }}>
          <table className="orders-table" id="inventory-table">
            <thead>
              <tr>
                <th>Product ID</th>
                <th>Product Name</th>
                <th>Current Stock</th>
                <th>Status &amp; Alert</th>
              </tr>
            </thead>
            <tbody>
              {inventory.map(item => {
                const isOutOfStock = item.stock <= 0;
                const isLowStock = item.stock > 0 && item.stock < LOW_STOCK_THRESHOLD;

                return (
                  <tr
                    key={item.productId}
                    className={`inventory-table-row ${isOutOfStock ? 'row-out-of-stock' : isLowStock ? 'row-low-stock' : ''}`}
                  >
                    <td><strong style={{ fontFamily: 'var(--font-mono)' }}>{item.productId}</strong></td>
                    <td>{item.name}</td>
                    <td style={{ fontFamily: 'var(--font-mono)', fontSize: '1.05rem', fontWeight: 700 }}>
                      {item.stock}
                    </td>
                    <td>
                      {isOutOfStock && (
                        <span className="stock-pill out-stock">
                          ✕ Out of Stock (0)
                        </span>
                      )}
                      {isLowStock && (
                        <span className="stock-pill low-stock-alert">
                          ⚠ Low Stock Alert ({item.stock} &lt; {LOW_STOCK_THRESHOLD}) — Reorder Needed
                        </span>
                      )}
                      {!isOutOfStock && !isLowStock && (
                        <span className="stock-pill in-stock">
                          ✓ Normal Stock ({item.stock})
                        </span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>

      {/* SECTION 4: Dual Feed: Order History (with Cancel) & Notification Activity Feed */}
      <div className="bottom-dual-grid">
        {/* Left: Order History & Audit Log */}
        <section className="glass-panel">
          <div className="card-header">
            <div>
              <div className="card-title">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
                </svg>
                Order History &amp; Actions
              </div>
              <div className="card-desc">GET /api/orders • Cancel button triggers POST /api/orders/{'{id}'}/cancel &amp; restocks items</div>
            </div>
            <button onClick={fetchOrders} className="chip-btn">↻ Refresh</button>
          </div>

          <div style={{ overflowX: 'auto' }}>
            <table className="orders-table" id="orders-history-table">
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Line Items</th>
                  <th>Status</th>
                  <th>Reason</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {orders.length > 0 ? (
                  orders.map(o => (
                    <tr key={o.orderId}>
                      <td style={{ fontFamily: 'var(--font-mono)', fontWeight: 700 }}>#{o.orderId}</td>
                      <td>
                        <div className="history-items-tags">
                          {o.items && o.items.length > 0 ? (
                            o.items.map((it, idx) => (
                              <span key={idx} className="item-pill-tag">
                                {it.quantity}x {it.productId}
                              </span>
                            ))
                          ) : (
                            <span style={{ color: 'var(--text-muted)' }}>None</span>
                          )}
                        </div>
                      </td>
                      <td>
                        <span className={`table-status ${o.status}`}>{o.status}</span>
                      </td>
                      <td style={{ fontSize: '0.825rem', maxWidth: '240px' }}>{o.reason}</td>
                      <td>
                        {o.status === 'CONFIRMED' ? (
                          <button
                            type="button"
                            className="cancel-order-btn"
                            onClick={() => handleCancelOrder(o.orderId)}
                            disabled={cancellingOrderId === o.orderId}
                            title="Cancel this order and return all reserved items to stock"
                          >
                            {cancellingOrderId === o.orderId ? 'Restocking...' : 'Cancel & Restock'}
                          </button>
                        ) : o.status === 'CANCELLED' ? (
                          <span className="cancelled-label">Cancelled</span>
                        ) : (
                          <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem' }}>N/A (Rejected)</span>
                        )}
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan="5" style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                      No orders placed yet.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>

        {/* Right: In-Monolith Domain Event Activity Feed (Notification Module) */}
        <section className="glass-panel">
          <div className="card-header">
            <div>
              <div className="card-title">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
                  <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
                </svg>
                Domain Event Notifications
              </div>
              <div className="card-desc">GET /api/notifications • Consumed via Spring @EventListener by Notification module</div>
            </div>
            <button onClick={fetchNotifications} className="chip-btn">↻ Refresh</button>
          </div>

          <div className="notifications-feed-body">
            {notifications.length > 0 ? (
              <div className="notifications-feed-list">
                {notifications.map(n => {
                  const isLowStock = n.message.includes('Low stock') || n.message.includes('reorder');
                  const isRejected = n.message.includes('rejected');
                  const isCancelled = n.message.includes('cancelled');
                  const isConfirmed = n.message.includes('confirmed');

                  return (
                    <div
                      key={n.notificationId}
                      className={`notification-card ${isLowStock ? 'notif-lowstock' : isRejected ? 'notif-rejected' : isCancelled ? 'notif-cancelled' : 'notif-confirmed'}`}
                    >
                      <div className="notif-icon">
                        {isLowStock && '⚠'}
                        {isRejected && '✕'}
                        {isCancelled && '↺'}
                        {isConfirmed && '✓'}
                      </div>
                      <div className="notif-content">
                        <div className="notif-message">{n.message}</div>
                        <div className="notif-meta">
                          <span>Notification #{n.notificationId}</span>
                          <span>•</span>
                          <span>{n.createdAt ? new Date(n.createdAt).toLocaleTimeString() : 'Just now'}</span>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <div className="empty-notifs-state">
                <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" style={{ opacity: 0.35 }}>
                  <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
                  <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
                </svg>
                <div style={{ fontWeight: 600, color: 'var(--text-secondary)', marginTop: '0.5rem' }}>No Notifications Yet</div>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                  Events published by Order &amp; Inventory modules will stream here.
                </div>
              </div>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}
