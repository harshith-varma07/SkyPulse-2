// Global variables
let currentUser = null;
let isLoggedIn = false;
const API_BASE_URL = 'http://localhost:8080/api';

// Initialize the application
document.addEventListener('DOMContentLoaded', function() {
    createParticles();
    
    // Check for existing user session
    const savedUser = localStorage.getItem('skyPulseUser');
    console.log('DOMContentLoaded - savedUser from localStorage:', savedUser);
    console.log('DOMContentLoaded - sessionStorage auth:', sessionStorage.getItem('authorization'));
    console.log('DOMContentLoaded - sessionStorage userId:', sessionStorage.getItem('userId'));
    
    if (savedUser) {
        try {
            currentUser = JSON.parse(savedUser);
            isLoggedIn = true;
            console.log('DOMContentLoaded - restored currentUser:', currentUser);
            console.log('DOMContentLoaded - set isLoggedIn to true');
            updateNavbarForLoggedInUser();
        } catch (error) {
            console.error('Error loading dashboard data:', error);
            showNotification('Error loading dashboard data. Using fallback data.', 'error');
            // Use complete fallback
            const fallbackCities = [
                { name: 'Delhi', country: 'India', aqi: 152, category: 'Moderate', coordinates: [28.6139, 77.2090] },
                { name: 'Mumbai', country: 'India', aqi: 89, category: 'Moderate', coordinates: [19.0760, 72.8777] }
            ];
            updateMainAQI(fallbackCities[0]);
            updateCityList(fallbackCities);
            updateParameters(fallbackCities[0]);
        } finally {
            showLoading(false);
        }
    }

// Update main AQI display
function updateMainAQI(cityData) {
    const aqiElement = document.getElementById('mainAqi');
    const categoryElement = document.getElementById('mainCategory');
    const cityElement = document.getElementById('mainCity');
    const progressElement = document.getElementById('aqiProgress');

    aqiElement.textContent = cityData.aqi;
    categoryElement.textContent = cityData.category;
    cityElement.textContent = `${cityData.name}, ${cityData.country}`;

    // Update progress ring
    const circumference = 2 * Math.PI * 90;
    const progress = (cityData.aqi / 300) * circumference;
    progressElement.style.strokeDashoffset = circumference - progress;

    // Update colors based on AQI
    const color = getAQIColor(cityData.aqi);
    aqiElement.className = `aqi-value ${color}`;
    progressElement.style.stroke = getAQIColorValue(cityData.aqi);

    // Animate the value
    animateValue(aqiElement, 0, cityData.aqi, 2000);
}

// Update city list
function updateCityList(cities) {
    const cityListElement = document.getElementById('cityList');
    cityListElement.innerHTML = '';

    cities.forEach((city, index) => {
        const cityCard = document.createElement('div');
        cityCard.className = 'city-card';
        cityCard.style.animationDelay = `${index * 0.1}s`;
        
        cityCard.innerHTML = `
            <div class="city-name">${city.name}, ${city.country}</div>
            <div class="city-aqi ${getAQIColor(city.aqi)}">${city.aqi}</div>
            <div style="color: var(--text-secondary); font-size: 0.9rem;">${city.category}</div>
        `;
        
        cityCard.addEventListener('click', () => selectCity(city));
        cityListElement.appendChild(cityCard);
    });
}

// Update parameters display
function updateParameters(cityData) {
    // Build parameters array using real data from cityData or fallback values
    const parameters = [
        { 
            name: 'PM2.5', 
            value: cityData?.pm25 || cityData?.data?.pm25 || 45.2, 
            unit: 'µg/m³', 
            color: 'var(--neon-blue)' 
        },
        { 
            name: 'PM10', 
            value: cityData?.pm10 || cityData?.data?.pm10 || 78.1, 
            unit: 'µg/m³', 
            color: 'var(--neon-green)' 
        },
        { 
            name: 'NO2', 
            value: cityData?.no2 || cityData?.data?.no2 || 32.5, 
            unit: 'µg/m³', 
            color: 'var(--neon-purple)' 
        },
        { 
            name: 'O3', 
            value: cityData?.o3 || cityData?.data?.o3 || 89.3, 
            unit: 'µg/m³', 
            color: '#ffff00' 
        },
        { 
            name: 'SO2', 
            value: cityData?.so2 || cityData?.data?.so2 || 15.7, 
            unit: 'µg/m³', 
            color: '#ff8c00' 
        },
        { 
            name: 'CO', 
            value: cityData?.co || cityData?.data?.co || 1.2, 
            unit: 'mg/m³', 
            color: '#ff6b6b' 
        }
    ];

    const parametersGrid = document.getElementById('parametersGrid');
    parametersGrid.innerHTML = '';

    parameters.forEach((param, index) => {
        const paramElement = document.createElement('div');
        paramElement.className = 'parameter-item';
        paramElement.style.animationDelay = `${index * 0.1}s`;
        
        // Format the parameter value to show appropriate decimal places
        let displayValue = param.value;
        if (typeof displayValue === 'number') {
            displayValue = displayValue.toFixed(1);
        }
        
        paramElement.innerHTML = `
            <div class="parameter-name">${param.name}</div>
            <div class="parameter-value" style="color: ${param.color};">${displayValue}</div>
            <div style="font-size: 0.7rem; color: var(--text-secondary);">${param.unit}</div>
        `;
        
        parametersGrid.appendChild(paramElement);
    });
}

// Search city functionality
async function searchCity() {
    const searchInput = document.getElementById('citySearch');
    const cityName = searchInput.value.trim();
    
    if (!cityName) return;

    showLoading(true);
    
    try {
        // First try to search for the city
        const response = await fetch(`${API_BASE_URL}/aqi/search?query=${encodeURIComponent(cityName)}`);
        const data = await response.json();
        
        if (data.success && data.currentData) {
            // Update main display with found city data
            const cityWithData = {
                name: data.currentData.city,
                country: '', // We don't have country info from backend
                aqi: data.currentData.aqiValue,
                category: data.currentData.category,
                pm25: data.currentData.pm25,
                pm10: data.currentData.pm10,
                no2: data.currentData.no2,
                so2: data.currentData.so2,
                co: data.currentData.co,
                o3: data.currentData.o3
            };
            updateMainAQI(cityWithData);
            updateParameters(cityWithData);
            showNotification(`Data loaded for ${data.currentData.city}!`, 'success');
        } else if (data.success && data.cities && data.cities.length > 0) {
            // If we found matching cities, try to get data for the first one
            const firstCity = data.cities[0];
            const cityResponse = await fetch(`${API_BASE_URL}/aqi/current/${encodeURIComponent(firstCity)}`);
            const cityData = await cityResponse.json();
            
            if (cityData.success) {
                const cityWithData = {
                    name: cityData.data.city,
                    country: '',
                    aqi: cityData.data.aqiValue,
                    category: cityData.data.category,
                    pm25: cityData.data.pm25,
                    pm10: cityData.data.pm10,
                    no2: cityData.data.no2,
                    so2: cityData.data.so2,
                    co: cityData.data.co,
                    o3: cityData.data.o3
                };
                updateMainAQI(cityWithData);
                updateParameters(cityWithData);
                showNotification(`Data loaded for ${cityData.data.city}!`, 'success');
            }
        } else {
            // Try to add the city to monitoring
            const addResponse = await fetch(`${API_BASE_URL}/aqi/cities/add?city=${encodeURIComponent(cityName)}`, {
                method: 'POST'
            });
            const addData = await addResponse.json();
            
            if (addData.success && addData.data) {
                const cityWithData = {
                    name: addData.data.city,
                    country: '',
                    aqi: addData.data.aqiValue,
                    category: addData.data.category,
                    pm25: addData.data.pm25,
                    pm10: addData.data.pm10,
                    no2: addData.data.no2,
                    so2: addData.data.so2,
                    co: addData.data.co,
                    o3: addData.data.o3
                };
                updateMainAQI(cityWithData);
                updateParameters(cityWithData);
                showNotification(`New city ${addData.data.city} added to monitoring!`, 'success');
            } else {
                showNotification('City not found or data unavailable. Please try another city.', 'error');
            }
        }
    } catch (error) {
        console.error('Error searching city:', error);
        showNotification('Error searching city. Please try again.', 'error');
    } finally {
        showLoading(false);
        searchInput.value = ''; // Clear search input
    }
}

// Select city from list
async function selectCity(city) {
    showLoading(true);
    
    try {
        const response = await fetch(`${API_BASE_URL}/aqi/current/${encodeURIComponent(city.name)}`);
        const data = await response.json();
        
        if (data.success && data.data) {
            const cityWithData = {
                name: data.data.city,
                country: city.country || '',
                aqi: data.data.aqiValue,
                category: data.data.category,
                pm25: data.data.pm25,
                pm10: data.data.pm10,
                no2: data.data.no2,
                so2: data.data.so2,
                co: data.data.co,
                o3: data.data.o3
            };
            updateMainAQI(cityWithData);
            updateParameters(cityWithData);
            showNotification(`Switched to ${data.data.city}`, 'success');
        } else {
            showNotification(`Unable to load data for ${city.name}`, 'error');
        }
    } catch (error) {
        console.error('Error selecting city:', error);
        showNotification(`Error loading data for ${city.name}`, 'error');
    } finally {
        showLoading(false);
    }
}

// Modal functions
function setAlert() {
    if (!isLoggedIn || !currentUser || !currentUser.token) {
        alert('Please login to set up alerts');
        openModal('loginModal');
        return;
    }
    openModal('alertModal');
}

function openModal(modalId) {
    const modal = document.getElementById(modalId);
    modal.style.display = 'block';
    document.body.style.overflow = 'hidden';
}

function closeModal(modalId) {
    const modal = document.getElementById(modalId);
    modal.style.display = 'none';
    document.body.style.overflow = 'auto';
}

// Login form handler
document.getElementById('loginForm').addEventListener('submit', async function(e) {
    e.preventDefault();
    
    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;
    
    if (!username || !password) {
        showNotification('Please fill in all fields', 'error');
        return;
    }
    
    try {
        showNotification('Logging in...', 'info');
        
        const response = await fetch(`${API_BASE_URL}/auth/login`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ username, password })
        });
        
        const data = await response.json();
        
        if (data.success) {
            currentUser = {
                id: data.userId,
                username: data.username,
                email: data.email,
                city: data.city,
                alertThreshold: data.alertThreshold,
                token: data.token // Store JWT
            };
            isLoggedIn = true;

            // Store user session and JWT
            localStorage.setItem('skyPulseUser', JSON.stringify(currentUser));
            sessionStorage.setItem('jwt', data.token);
            sessionStorage.setItem('userId', data.userId.toString());

            // Update UI for logged-in user
            updateNavbarForLoggedInUser();

            // Update auth-dependent UI components
            updateReportUI();

            // Close modal and clean up
            closeModal('loginModal');
            document.getElementById('loginForm').reset();

            // Show success message
            showNotification(`Welcome back, ${currentUser.username}!`, 'success');

            // Load user-specific data
            loadUserAlerts();

        } else {
            showNotification(data.message || 'Invalid credentials. Please try again.', 'error');
        }
    } catch (error) {
        console.error('Login error:', error);
        showNotification('Login failed. Please check your connection and try again.', 'error');
    }
});

// Alert form handler
document.getElementById('alertForm').addEventListener('submit', async function(e) {
    e.preventDefault();
    
    if (!isLoggedIn) {
        showNotification('Please login to set up alerts.', 'error');
        return;
    }
    
    const city = document.getElementById('alertCity').value;
    const threshold = document.getElementById('alertThreshold').value;
    
    try {
        const response = await fetch(`${API_BASE_URL}/alerts/create?city=${encodeURIComponent(city)}&threshold=${threshold}`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${currentUser.token}`
            }
        });

        const data = await response.json();

        if (data.success) {
            closeModal('alertModal');
            showNotification('Alert settings updated successfully!', 'success');
            document.getElementById('alertForm').reset();
            loadUserAlerts();
        } else {
            showNotification(data.message || 'Failed to create alert. Please try again.', 'error');
        }
    } catch (error) {
        console.error('Alert creation error:', error);
        showNotification('Failed to create alert. Please try again.', 'error');
    }
});

// Registration form handler
document.getElementById('registerForm').addEventListener('submit', async function(e) {
    e.preventDefault();
    
    const username = document.getElementById('regUsername').value;
    const email = document.getElementById('regEmail').value;
    const password = document.getElementById('regPassword').value;
    const phoneNumber = document.getElementById('regPhone').value;
    const city = document.getElementById('regCity').value;
    const alertThreshold = document.getElementById('regThreshold').value;
    
    if (!username || !email || !password) {
        showNotification('Please fill in all required fields', 'error');
        return;
    }
    
    try {
        showNotification('Creating account...', 'info');
        
        const response = await fetch(`${API_BASE_URL}/auth/register`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                username,
                email,
                password,
                phoneNumber,
                city,
                alertThreshold: parseInt(alertThreshold)
            })
        });
        
        const data = await response.json();
        
        if (data.success) {
            closeModal('registerModal');
            showNotification('Account created successfully! Please login.', 'success');
            document.getElementById('registerForm').reset();
            openModal('loginModal');
        } else {
            showNotification(data.message || 'Registration failed. Please try again.', 'error');
        }
    } catch (error) {
        console.error('Registration error:', error);
        showNotification('Registration failed. Please check your connection and try again.', 'error');
    }
});

// Update navbar for logged-in user
function updateNavbarForLoggedInUser() {
    const loginBtn = document.querySelector('.login-btn');
    loginBtn.innerHTML = `<i class="fas fa-user-circle"></i> ${currentUser.username}`;
    loginBtn.onclick = () => showUserMenu();
    
    // Show historical data card for premium users
    showHistoricalDataCard();
}

// Show user menu
function showUserMenu() {
    // Create dropdown menu for logged-in user
    const menu = document.createElement('div');
    menu.className = 'user-menu';
    menu.style.cssText = `
        position: absolute;
        top: 100%;
        right: 0;
        background: var(--card-bg);
        backdrop-filter: blur(20px);
        border: 1px solid var(--glass-border);
        border-radius: 10px;
        padding: 1rem;
        min-width: 200px;
        z-index: 1001;
    `;
    
    menu.innerHTML = `
        <div style="padding: 0.5rem 0; border-bottom: 1px solid var(--glass-border); margin-bottom: 0.5rem;">
            <strong>${currentUser.username}</strong><br>
            <small style="color: var(--text-secondary);">${currentUser.email}</small>
        </div>
        <a href="analytics.html" style="display: block; padding: 0.5rem 0; color: var(--text-primary); text-decoration: none;">
            <i class="fas fa-chart-bar"></i> Analytics
        </a>
        <a href="#" onclick="seedHistoricalDataFromMenu()" style="display: block; padding: 0.5rem 0; color: var(--text-primary); text-decoration: none;">
            <i class="fas fa-database"></i> Generate Sample Data
        </a>
        <a href="#" onclick="viewAlerts()" style="display: block; padding: 0.5rem 0; color: var(--text-primary); text-decoration: none;">
            <i class="fas fa-bell"></i> My Alerts
        </a>
        <a href="#" onclick="logout()" style="display: block; padding: 0.5rem 0; color: var(--text-primary); text-decoration: none;">
            <i class="fas fa-sign-out-alt"></i> Logout
        </a>
    `;
    
    // Remove existing menu if any
    const existingMenu = document.querySelector('.user-menu');
    if (existingMenu) {
        existingMenu.remove();
    }
    
    // Add menu to navbar
    const navContainer = document.querySelector('.nav-container');
    navContainer.style.position = 'relative';
    navContainer.appendChild(menu);
    
    // Close menu when clicking outside
    setTimeout(() => {
        document.addEventListener('click', function closeMenu(e) {
            if (!menu.contains(e.target)) {
                menu.remove();
                document.removeEventListener('click', closeMenu);
            }
        });
    }, 100);
}

// View alerts function
function viewAlerts() {
    const userMenu = document.querySelector('.user-menu');
    if (userMenu) {
        userMenu.remove();
    }
    
    // Scroll to alerts section or show modal with alerts
    const alertSection = document.querySelector('.glass-card h3');
    if (alertSection) {
        alertSection.scrollIntoView({ behavior: 'smooth' });
        showNotification('Your alerts are displayed in the panel below', 'info');
    }
}

// Download PDF report
async function downloadReport() {
    if (!isLoggedIn) {
        showNotification('Please login to download reports.', 'error');
        return;
    }
    
    try {
        showNotification('Generating PDF report...', 'info');
        
        const mainCity = document.getElementById('mainCity').textContent.split(',')[0] || 'Delhi';
        
        const response = await fetch(`${API_BASE_URL}/export/pdf?city=${encodeURIComponent(mainCity)}`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${currentUser.token}`
            }
        });
        
        if (response.ok) {
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `air-quality-report-${mainCity}-${new Date().toISOString().split('T')[0]}.pdf`;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            window.URL.revokeObjectURL(url);
            
            showNotification('Report downloaded successfully!', 'success');
        } else {
            const errorText = await response.text();
            showNotification('Failed to generate report: ' + errorText, 'error');
        }
    } catch (error) {
        console.error('Download error:', error);
        showNotification('Failed to download report.', 'error');
    }
}

// Load user alerts
async function loadUserAlerts() {
    if (!isLoggedIn) return;
    
    try {
        const response = await fetch(`${API_BASE_URL}/users/alerts`, {
            headers: {
                'Authorization': `Bearer ${currentUser.token}`
            }
        });

        const data = await response.json();

        if (data.success) {
            updateAlertsList(data.alerts);
        } else {
            console.warn('Failed to load user alerts:', data.message);
        }
    } catch (error) {
        console.error('Error loading alerts:', error);
    }
}

// Update alerts list
function updateAlertsList(alerts) {
    const alertList = document.getElementById('alertList');
    alertList.innerHTML = '';
    
    if (alerts.length === 0) {
        alertList.innerHTML = '<p style="color: var(--text-secondary); text-align: center;">No alerts set up yet.</p>';
        return;
    }
    
    alerts.forEach(alert => {
        const alertItem = document.createElement('div');
        alertItem.className = 'alert-item';
        alertItem.innerHTML = `
            <div style="display: flex; justify-content: space-between; align-items: center;">
                <div>
                    <strong>${alert.city}, ${alert.country}</strong><br>
                    <small>Threshold: AQI > ${alert.thresholdValue}</small>
                </div>
                <button onclick="deleteAlert(${alert.id})" style="background: var(--danger-gradient); border: none; border-radius: 5px; padding: 0.5rem; color: white; cursor: pointer;">
                    <i class="fas fa-trash"></i>
                </button>
            </div>
        `;
        alertList.appendChild(alertItem);
    });
}

// Delete alert
async function deleteAlert(alertId) {
    try {
        const response = await fetch(`${API_BASE_URL}/alerts/${alertId}`, {
            method: 'DELETE',
            headers: {
                'Authorization': `Bearer ${currentUser.token}`
            }
        });
        
        if (response.ok) {
            showNotification('Alert deleted successfully!', 'success');
            loadUserAlerts();
        } else {
            showNotification('Failed to delete alert.', 'error');
        }
    } catch (error) {
        console.error('Delete alert error:', error);
        showNotification('Failed to delete alert.', 'error');
    }
}

// Logout function
function logout() {
    currentUser = null;
    isLoggedIn = false;
    // Clear stored session
    localStorage.removeItem('skyPulseUser');
    sessionStorage.removeItem('jwt');
    sessionStorage.removeItem('userId');
    // Hide historical data card
    hideHistoricalDataCard();
    // Reset navbar
    const loginBtn = document.querySelector('.login-btn');
    loginBtn.innerHTML = '<i class="fas fa-user"></i> Login';
    loginBtn.onclick = () => openModal('loginModal');
    // Remove user menu
    const userMenu = document.querySelector('.user-menu');
    if (userMenu) {
        userMenu.remove();
    }
    // Reset alert list to default
    const alertList = document.getElementById('alertList');
    alertList.innerHTML = `
        <div class="alert-item">
            <strong>Login Required</strong><br>
            Please login to view personalized alerts<br>
            <small>Register to get SMS notifications</small>
        </div>
    `;
    showNotification('Logged out successfully!', 'success');
}

// Utility functions
function getAQIColor(aqi) {
    if (aqi <= 50) return 'aqi-good';
    if (aqi <= 100) return 'aqi-moderate';
    if (aqi <= 150) return 'aqi-unhealthy';
    if (aqi <= 200) return 'aqi-very-unhealthy';
    return 'aqi-hazardous';
}

function getAQIColorValue(aqi) {
    if (aqi <= 50) return 'var(--neon-green)';
    if (aqi <= 100) return '#f59e0b';
    if (aqi <= 150) return '#f97316';
    if (aqi <= 200) return '#ef4444';
    return '#dc2626';
}

function animateValue(element, start, end, duration) {
    const range = end - start;
    const startTime = performance.now();
    
    function update(currentTime) {
        const elapsed = currentTime - startTime;
        const progress = Math.min(elapsed / duration, 1);
        const currentValue = Math.floor(start + range * progress);
        
        element.textContent = currentValue;
        
        if (progress < 1) {
            requestAnimationFrame(update);
        }
    }
    
    requestAnimationFrame(update);
}

function showLoading(show) {
    const loading = document.getElementById('loading');
    const dashboard = document.getElementById('dashboard');
    
    if (show) {
        loading.style.display = 'block';
        dashboard.style.opacity = '0.5';
    } else {
        loading.style.display = 'none';
        dashboard.style.opacity = '1';
    }
}

function showNotification(message, type = 'info') {
    const notification = document.createElement('div');
    notification.style.cssText = `
        position: fixed;
        top: 100px;
        right: 20px;
        background: var(--card-bg);
        backdrop-filter: blur(20px);
        border: 1px solid var(--glass-border);
        border-radius: 10px;
        padding: 1rem 1.5rem;
        color: var(--text-primary);
        z-index: 3000;
        animation: slideInRight 0.3s ease;
        max-width: 300px;
        box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
    `;
    
    const icon = type === 'success' ? 'check-circle' : type === 'error' ? 'exclamation-circle' : 'info-circle';
    const color = type === 'success' ? 'var(--neon-green)' : type === 'error' ? '#ff0000' : 'var(--neon-blue)';
    
    notification.innerHTML = `
        <div style="display: flex; align-items: center; gap: 0.5rem;">
            <i class="fas fa-${icon}" style="color: ${color};"></i>
            <span>${message}</span>
        </div>
    `;
    
    document.body.appendChild(notification);
    
    setTimeout(() => {
        notification.style.animation = 'slideOutRight 0.3s ease';
        setTimeout(() => {
            if (notification.parentNode) {
                notification.parentNode.removeChild(notification);
            }
        }, 300);
    }, 3000);
}

// Add slide animations to CSS
const style = document.createElement('style');
style.textContent = `
    @keyframes slideInRight {
        from { transform: translateX(100%); opacity: 0; }
        to { transform: translateX(0); opacity: 1; }
    }
    
    @keyframes slideOutRight {
        from { transform: translateX(0); opacity: 1; }
        to { transform: translateX(100%); opacity: 0; }
    }
`;
document.head.appendChild(style);

// Handle Enter key in search
document.getElementById('citySearch').addEventListener('keypress', function(e) {
    if (e.key === 'Enter') {
        searchCity();
    }
});

// Close modals when clicking outside
window.addEventListener('click', function(e) {
    if (e.target.classList.contains('modal')) {
        e.target.style.display = 'none';
        document.body.style.overflow = 'auto';
    }
});

// Update last updated time
function updateLastUpdatedTime() {
    const now = new Date();
    const lastUpdatedElement = document.getElementById('lastUpdated');
    lastUpdatedElement.textContent = 'Just now';
    
    // Update to relative time after 1 minute
    setTimeout(() => {
        const minutes = Math.floor((new Date() - now) / 60000);
        if (minutes < 1) {
            lastUpdatedElement.textContent = 'Just now';
        } else if (minutes === 1) {
            lastUpdatedElement.textContent = '1 minute ago';
        } else {
            lastUpdatedElement.textContent = `${minutes} minutes ago`;
        }
    }, 60000);
}

// Real-time updates simulation
setInterval(() => {
    // Simulate real-time AQI changes
    const currentAqi = parseInt(document.getElementById('mainAqi').textContent);
    const change = Math.floor(Math.random() * 10) - 5; // Random change between -5 and +5
    const newAqi = Math.max(0, Math.min(500, currentAqi + change));
    
    if (newAqi !== currentAqi) {
        document.getElementById('mainAqi').textContent = newAqi;
        
        // Update color and category
        const color = getAQIColor(newAqi);
        document.getElementById('mainAqi').className = `aqi-value ${color}`;
        
        // Update progress ring
        const progressElement = document.getElementById('aqiProgress');
        const circumference = 2 * Math.PI * 90;
        const progress = (newAqi / 300) * circumference;
        progressElement.style.strokeDashoffset = circumference - progress;
        progressElement.style.stroke = getAQIColorValue(newAqi);
        
        updateLastUpdatedTime();
    }
}, 30000); // Update every 30 seconds

// Historical Data Functions
function showHistoricalDataCard() {
    // This function is called when user logs in - update all auth-dependent UI
    if (isLoggedIn) {
        // Update PDF reports UI to show controls instead of login button
        updateReportUI();
    }
}

function hideHistoricalDataCard() {
    // This function is called when user logs out - hide auth-dependent UI
    updateReportUI();
    if (typeof ChartManager !== 'undefined' && ChartManager.destroy) {
        ChartManager.destroy();
    }
}

// Function removed - date inputs no longer needed for simplified PDF generation

async function loadHistoricalData() {
    if (!isLoggedIn) {
        showNotification('Please login to view historical data', 'error');
        return;
    }
    
    const startDate = document.getElementById('startDate').value;
    const endDate = document.getElementById('endDate').value;
    const currentCity = document.getElementById('cityName').textContent;
    
    if (!startDate || !endDate) {
        showNotification('Please select both start and end dates', 'error');
        return;
    }
    
    if (new Date(startDate) >= new Date(endDate)) {
        showNotification('Start date must be before end date', 'error');
        return;
    }
    
    try {
        showLoading(true);
        
        const authHeader = sessionStorage.getItem('authorization');
        const userId = sessionStorage.getItem('userId');
        
        const response = await fetch(
            `${API_BASE_URL}/aqi/historical/${encodeURIComponent(currentCity)}?startDate=${encodeURIComponent(startDate)}&endDate=${encodeURIComponent(endDate)}`,
            {
                headers: {
                    'Authorization': `Bearer ${currentUser.token}`,
                    'Content-Type': 'application/json'
                }
            }
        );

        const data = await response.json();

        if (data.success && data.data && data.data.length > 0) {
            ChartManager.create(data.data, currentCity);
            const stats = ChartManager.calculateStats(data.data);
            ChartManager.updateStats(stats);
            showNotification(`Loaded ${data.count} data points for ${currentCity}`, 'success');
        } else if (data.requiresAuth) {
            showNotification('Please login to access historical data', 'error');
            openModal('loginModal');
        } else {
            showNotification('No historical data found for the selected period', 'warning');
        }
        
    } catch (error) {
        console.error('Error loading historical data:', error);
        showNotification('Error loading historical data. Please try again.', 'error');
    } finally {
        showLoading(false);
    }
}

// Initialize with sample data on load
setTimeout(() => {
    updateLastUpdatedTime();
}, 1000);

// Seed historical data from menu
async function seedHistoricalDataFromMenu() {
    const userMenu = document.querySelector('.user-menu');
    if (userMenu) {
        userMenu.remove();
    }
    
    if (!confirm('This will generate 18 months of sample historical data for better analytics experience. This may take a few minutes. Continue?')) {
        return;
    }
    
    try {
        showNotification('Starting historical data generation...', 'info');
        
        const response = await fetch(`${API_BASE_URL}/admin/seed-historical-data?years=1.5`, {
            method: 'POST'
        });
        
        const data = await response.json();
        
        if (data.success) {
            showNotification('Historical data generation started! Check the Analytics page in a few minutes.', 'success');
        } else {
            showNotification(data.message || 'Failed to start data generation', 'error');
        }
    } catch (error) {
        console.error('Error seeding data:', error);
        showNotification('Error starting data generation', 'error');
    }
}

// Navigation functions
function showHome() {
    // Already on home page, just refresh the dashboard
    loadDashboardData();
    
    // Scroll to top
    window.scrollTo({
        top: 0,
        behavior: 'smooth'
    });
    
    // Update active nav link
    document.querySelectorAll('.nav-link').forEach(link => {
        link.classList.remove('active');
    });
    document.querySelector('.nav-link[onclick="showHome()"]').classList.add('active');
}

// Load supported cities for footer
async function loadSupportedCities() {
    const citiesDiv = document.getElementById('supportedCities');
    if (!citiesDiv) return; // Footer might not exist on all pages
    
    try {
        const response = await fetch(`${API_BASE_URL}/admin/database-status`);
        const data = await response.json();

        if (data.success && data.availableCities && data.availableCities.length > 0) {
            const citiesHTML = data.availableCities.map(city => 
                `<span class="city-tag">${city}</span>`
            ).join('');
            
            citiesDiv.innerHTML = citiesHTML;
        } else {
            citiesDiv.innerHTML = '<span class="no-cities">No cities available</span>';
        }
    } catch (error) {
        citiesDiv.innerHTML = '<span class="error-cities">Unable to load cities</span>';
    }
}

// PDF Report functionality
function updateReportUI() {
    const userSession = getUserSession();
    const reportControls = document.getElementById('reportControls');
    const loginForReports = document.getElementById('loginForReports');
    
    // Check if user is logged in using either system
    const isUserLoggedIn = (userSession && userSession.id) || (isLoggedIn && currentUser && currentUser.id);
    
    if (isUserLoggedIn) {
        // User is logged in - show report controls
        if (reportControls) reportControls.style.display = 'block';
        if (loginForReports) loginForReports.style.display = 'none';
    } else {
        // User is not logged in - show login button
        if (reportControls) reportControls.style.display = 'none';
        if (loginForReports) loginForReports.style.display = 'block';
    }
}

async function downloadPDFReport() {
    const currentCity = getCurrentCity();
    if (!currentCity) {
        showNotification('Please search for a city first before generating a report', 'error');
        return;
    }

    if (!isLoggedIn || !currentUser || !currentUser.token) {
        showNotification('Please login to download PDF reports', 'error');
        openModal('loginModal');
        return;
    }

    try {
        showNotification('Generating PDF report for all available data...', 'info');

        const response = await fetch(`${API_BASE_URL}/export/pdf/${encodeURIComponent(currentCity)}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${currentUser.token}`
            }
        });

        if (response.ok) {
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.style.display = 'none';
            a.href = url;
            a.download = `air_quality_report_${currentCity.replace(/[^a-zA-Z0-9]/g, '_')}_${new Date().toISOString().slice(0, 10)}.pdf`;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            window.URL.revokeObjectURL(url);

            showNotification('Report downloaded successfully!', 'success');
        } else {
            const errorText = await response.text();
            showNotification('Failed to generate report: ' + errorText, 'error');
        }
    } catch (error) {
        console.error('Download error:', error);
        showNotification('Failed to download report.', 'error');
    }
}})