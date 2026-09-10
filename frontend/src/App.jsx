import React, { useState, useEffect } from 'react';
import './App.css';

const API_BASE_URL = 'http://localhost:8080';

export default function App() {
  const [inventory, setInventory] = useState([]);
  const [selectedProductId, setSelectedProductId] = useState('P100');
  const [quantity, setQuantity] = useState(1);
  const [loading, setLoading] = useState(false);
  const [orders, setOrders] = useState([]);
  
  // Last Order Result
  const [lastResult, setLastResult] = useState(null);

  // Fetch initial inventory and order history
  useEffect(() => {
    fetchInventory();
    fetchOrders();
  }, []);

  const fetchInventory = async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/api/inventory`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      setInventory(data);
      if (data.length > 0 && !selectedProductId) {
        setSelectedProductId(data[0].productId);
      }
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

  const handleQuantityChange = (newVal) => {
    const val = Math.max(1, parseInt(newVal, 10) || 1);
    setQuantity(val);
  };

  const handleSubmitOrder = async (e) => {
    e.preventDefault();
    if (!selectedProductId || quantity <= 0) return;

    setLoading(true);
    try {
      const res = await fetch(`${API_BASE_URL}/api/orders`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          productId: selectedProductId,
          quantity: Number(quantity)
        })
      });

      if (!res.ok) {
        const errText = await res.text();
        throw new Error(errText || `Server error: ${res.status}`);
      }

      const data = await res.json();
      setLastResult({
        status: data.status,
        reason: data.reason,
        requestedProduct: selectedProductId,
        requestedQuantity: quantity,
        orderId: data.orderId,
        inventory: data.inventory
      });

      // Refresh UI data directly from Supabase via backend
      await fetchInventory();
      await fetchOrders();
    } catch (err) {
      console.error('Order submission error:', err);
      setLastResult({
        status: 'REJECTED',
        reason: err.message || 'Unexpected error while placing order',
        requestedProduct: selectedProductId,
        requestedQuantity: quantity
      });
    } finally {
      setLoading(false);
    }
  };

  const selectedProduct = inventory.find(p => p.productId === selectedProductId);

  return (
    <div className="app-container">
      {/* Header */}
      <header className="app-header">
        <div>
          <div className="brand-badge">
            <span>Modular Monolith</span>
            <span>•</span>
            <span>Pescante</span>
          </div>
          <h1 className="brand-title">Order & Inventory System</h1>
          <p className="brand-subtitle">
            In-process Spring Boot integration (<code>edu.cit.pescante.shop</code> &amp; <code>edu.cit.pescante.inventory</code>) backed by Supabase Postgres
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
        </div>
      </header>

      {/* Live Inventory Status Overview */}
      <section style={{ marginBottom: '2.5rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h2 style={{ fontSize: '1.15rem', fontWeight: 700, color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            Live Inventory Catalog
          </h2>
          <button 
            onClick={fetchInventory}
            className="chip-btn"
            title="Refresh stock levels from database"
          >
            ↻ Refresh Stock
          </button>
        </div>

        <div className="inventory-grid">
          {inventory.map(item => (
            <div 
              key={item.productId}
              className="product-card"
              onClick={() => setSelectedProductId(item.productId)}
              style={{
                cursor: 'pointer',
                borderColor: selectedProductId === item.productId ? 'var(--indigo-500)' : 'var(--border-subtle)',
                background: selectedProductId === item.productId ? 'rgba(99, 102, 241, 0.08)' : 'var(--bg-card)'
              }}
            >
              <div className="product-info">
                <h4>{item.name}</h4>
                <span>{item.productId}</span>
              </div>
              <div className={`stock-pill ${item.stock > 0 ? 'in-stock' : 'out-stock'}`}>
                {item.stock > 0 ? `${item.stock} in stock` : 'Out of stock'}
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Main Grid: Order Form + Result Area */}
      <div className="main-grid">
        {/* Order Form Card */}
        <div className="glass-panel">
          <div className="card-header">
            <div>
              <div className="card-title">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="8" cy="21" r="1"/><circle cx="19" cy="21" r="1"/>
                  <path d="M2.05 2.05h2l2.66 12.42a2 2 0 0 0 2 1.58h9.78a2 2 0 0 0 1.95-1.57l1.65-7.43H5.12"/>
                </svg>
                Place New Order
              </div>
              <div className="card-desc">HTTP POST /api/orders (triggers in-process inventory reservation)</div>
            </div>
          </div>

          <div className="card-body">
            <form onSubmit={handleSubmitOrder}>
              {/* Product Selection */}
              <div className="form-group">
                <label className="form-label" htmlFor="product-select">Select Product</label>
                <div className="select-wrapper">
                  <select 
                    id="product-select"
                    className="custom-select"
                    value={selectedProductId}
                    onChange={(e) => setSelectedProductId(e.target.value)}
                  >
                    {inventory.map(p => (
                      <option key={p.productId} value={p.productId}>
                        {p.productId} — {p.name} ({p.stock} available)
                      </option>
                    ))}
                  </select>
                  <div className="select-arrow">▼</div>
                </div>
              </div>

              {/* Quantity Stepper */}
              <div className="form-group">
                <label className="form-label" htmlFor="quantity-input">Order Quantity</label>
                <div className="quantity-stepper">
                  <button 
                    type="button"
                    className="stepper-btn"
                    onClick={() => handleQuantityChange(quantity - 1)}
                    disabled={quantity <= 1 || loading}
                    aria-label="Decrease quantity"
                  >
                    −
                  </button>
                  <input 
                    id="quantity-input"
                    type="number"
                    min="1"
                    className="quantity-input"
                    value={quantity}
                    onChange={(e) => handleQuantityChange(e.target.value)}
                    disabled={loading}
                  />
                  <button 
                    type="button"
                    className="stepper-btn"
                    onClick={() => handleQuantityChange(quantity + 1)}
                    disabled={loading}
                    aria-label="Increase quantity"
                  >
                    +
                  </button>
                </div>

                {/* Preset Chips */}
                <div className="quick-quantities">
                  {[1, 2, 5, 10].map(n => (
                    <button
                      key={n}
                      type="button"
                      className="chip-btn"
                      onClick={() => setQuantity(n)}
                    >
                      +{n}
                    </button>
                  ))}
                  {selectedProduct && selectedProduct.stock > 0 && (
                    <button
                      type="button"
                      className="chip-btn"
                      onClick={() => setQuantity(selectedProduct.stock)}
                    >
                      Max ({selectedProduct.stock})
                    </button>
                  )}
                  {/* Quick test preset for rejected path */}
                  <button
                    type="button"
                    className="chip-btn"
                    style={{ borderColor: 'var(--rose-border)', color: 'var(--rose-400)' }}
                    onClick={() => {
                      if (selectedProduct && selectedProduct.stock > 0) {
                        setQuantity(selectedProduct.stock + 10);
                      } else {
                        setQuantity(1);
                      }
                    }}
                    title="Set quantity higher than stock to test REJECTED path"
                  >
                    Exceed Stock (Test Reject)
                  </button>
                </div>
              </div>

              {/* Submit Button */}
              <button 
                id="submit-order-btn"
                type="submit" 
                className="submit-btn" 
                disabled={loading || !selectedProductId}
              >
                {loading ? (
                  <span>Processing In-Process Reservation...</span>
                ) : (
                  <>
                    <span>Submit Order via REST</span>
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                      <line x1="5" y1="12" x2="19" y2="12"></line>
                      <polyline points="12 5 19 12 12 19"></polyline>
                    </svg>
                  </>
                )}
              </button>
            </form>
          </div>
        </div>

        {/* Result Showcase Card */}
        <div className="glass-panel">
          <div className="card-header">
            <div>
              <div className="card-title">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                  <polyline points="22 4 12 14.01 9 11.01"/>
                </svg>
                Order Result Area
              </div>
              <div className="card-desc">Evaluated by OrderService &amp; InventoryService</div>
            </div>
          </div>

          <div className="card-body">
            {lastResult ? (
              <div 
                id="order-result-area"
                className={`result-container ${lastResult.status.toLowerCase()} animate-pop-in`}
              >
                <div className={`status-badge-lg ${lastResult.status.toLowerCase()}`}>
                  {lastResult.status === 'CONFIRMED' ? (
                    <>
                      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                        <polyline points="20 6 9 17 4 12"/>
                      </svg>
                      CONFIRMED
                    </>
                  ) : (
                    <>
                      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                        <circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>
                      </svg>
                      REJECTED
                    </>
                  )}
                </div>

                <div className="result-reason">
                  {lastResult.reason}
                </div>

                <div className="result-meta-grid">
                  <div className="meta-item">
                    <span className="meta-label">Product</span>
                    <span className="meta-val">{lastResult.requestedProduct}</span>
                  </div>
                  <div className="meta-item">
                    <span className="meta-label">Quantity</span>
                    <span className="meta-val">{lastResult.requestedQuantity} unit(s)</span>
                  </div>
                  <div className="meta-item">
                    <span className="meta-label">Current Stock</span>
                    <span className="meta-val">
                      {lastResult.inventory ? `${lastResult.inventory.stock} remaining` : 'N/A'}
                    </span>
                  </div>
                  <div className="meta-item">
                    <span className="meta-label">Order Record ID</span>
                    <span className="meta-val">#{lastResult.orderId || 'Audit Logged'}</span>
                  </div>
                </div>
              </div>
            ) : (
              <div className="result-container idle">
                <svg width="42" height="42" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" style={{ opacity: 0.4, marginBottom: '0.75rem' }}>
                  <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/>
                  <line x1="8" y1="21" x2="16" y2="21"/>
                  <line x1="12" y1="17" x2="12" y2="21"/>
                </svg>
                <div style={{ fontWeight: 600, color: 'var(--text-secondary)' }}>No orders submitted yet</div>
                <div style={{ fontSize: '0.825rem', marginTop: '0.25rem' }}>
                  Select a product, choose quantity, and click Submit Order to see CONFIRMED or REJECTED results.
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Order History / Audit Log */}
      <section className="glass-panel" style={{ marginTop: '2.5rem' }}>
        <div className="card-header">
          <div>
            <div className="card-title">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
              </svg>
              Order Audit Log (Database <code>orders</code> table)
            </div>
            <div className="card-desc">Every order attempt is recorded with status, reason, and timestamp</div>
          </div>
          <button onClick={fetchOrders} className="chip-btn">↻ Refresh</button>
        </div>

        <div style={{ overflowX: 'auto' }}>
          <table className="orders-table" id="orders-history-table">
            <thead>
              <tr>
                <th>Order ID</th>
                <th>Product</th>
                <th>Qty</th>
                <th>Status</th>
                <th>Reason</th>
                <th>Timestamp</th>
              </tr>
            </thead>
            <tbody>
              {orders.length > 0 ? (
                orders.map((o) => (
                  <tr key={o.orderId}>
                    <td style={{ fontFamily: 'var(--font-mono)', fontWeight: 600 }}>#{o.orderId}</td>
                    <td><strong>{o.productId}</strong></td>
                    <td style={{ fontFamily: 'var(--font-mono)' }}>{o.quantity}</td>
                    <td>
                      <span className={`table-status ${o.status}`}>{o.status}</span>
                    </td>
                    <td style={{ maxWidth: '380px' }}>{o.reason}</td>
                    <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                      {o.createdAt ? new Date(o.createdAt).toLocaleString() : 'Just now'}
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan="6" style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                    No order history recorded yet in the orders table.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
