// analytics.js - Extended Analytics for SkyPulse
// Extends charts.js functionality with additional chart types

// Global variables
let currentUser = null;
let isLoggedIn = false;
let analyticsData = null;
let activeCharts = {};
const API_BASE_URL = 'http://localhost:8080/api';

// Initialize the analytics page
document.addEventListener('DOMContentLoaded', function() {
    createParticles();
    initializeDateInputs();
    checkUserAuthentication();
    loadAvailableCities();
    loadSupportedCities(); // Load footer cities
    
    // Add event listener for city selection changes
    const citySelect = document.getElementById('citySelect');
    if (citySelect) {
        citySelect.addEventListener('change', function() {
            loadDataAvailability();
        });
    }
});

// Create floating particles (reuse from main script)
function createParticles() {
    const particlesContainer = document.getElementById('particles');
    const particleCount = 30; // Fewer particles for analytics page

    for (let i = 0; i < particleCount; i++) {
        const particle = document.createElement('div');
        particle.className = 'particle';
        particle.style.left = Math.random() * 100 + '%';
        particle.style.animationDelay = Math.random() * 20 + 's';
        particle.style.animationDuration = (Math.random() * 10 + 10) + 's';
        
        const colors = ['var(--neon-blue)', 'var(--neon-purple)', 'var(--neon-green)'];
        particle.style.background = colors[Math.floor(Math.random() * colors.length)];
        
        particlesContainer.appendChild(particle);
    }
}

// Initialize date inputs with default values (optimized ranges for 18 months of real-time data)
function initializeDateInputs() {
    const now = new Date();
    const endDate = new Date(now);
    const startDate = new Date(now);
    
    // Set default to last 90 days for optimal performance while showing meaningful data
    startDate.setDate(startDate.getDate() - 90);
    
    const formatDateForInput = (date) => {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        return `${year}-${month}-${day}T${hours}:${minutes}`;
    };
    
    // Set the input values
    document.getElementById('startDate').value = formatDateForInput(startDate);
    document.getElementById('endDate').value = formatDateForInput(endDate);
    
    // Set the input min/max values to cover the full 18-month period
    const eighteenMonthsAgo = new Date(now);
    eighteenMonthsAgo.setMonth(eighteenMonthsAgo.getMonth() - 18);
    
    const startDateInput = document.getElementById('startDate');
    const endDateInput = document.getElementById('endDate');
    
    startDateInput.min = formatDateForInput(eighteenMonthsAgo);
    startDateInput.max = formatDateForInput(now);
    endDateInput.min = formatDateForInput(eighteenMonthsAgo);
    endDateInput.max = formatDateForInput(now);
    
    // Load data availability info after a short delay
    setTimeout(loadDataAvailability, 1000);
}

// Quick date range selection function
function setDateRange(days) {
    const now = new Date();
    const endDate = new Date(now);
    const startDate = new Date(now);
    
    // Subtract the specified number of days
    startDate.setDate(startDate.getDate() - days);
    
    const formatDateForInput = (date) => {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        return `${year}-${month}-${day}T${hours}:${minutes}`;
    };
    
    // Set the input values
    document.getElementById('startDate').value = formatDateForInput(startDate);
    document.getElementById('endDate').value = formatDateForInput(endDate);
    
    // Update button states
    document.querySelectorAll('.date-range-btn').forEach(btn => {
        btn.classList.remove('active');
    });
    
    // Mark the clicked button as active
    event.target.classList.add('active');
    
    // Show a subtle notification
    showNotification(`Date range set to last ${days} days`, 'success');
}

// Load and display data availability information
async function loadDataAvailability() {
    try {
        const citySelect = document.getElementById('citySelect');
        const selectedCity = citySelect.value;
        
        const response = await fetch(`${API_BASE_URL}/aqi/data-availability${selectedCity ? `?city=${encodeURIComponent(selectedCity)}` : ''}`);
        const data = await response.json();
        
        if (data.success) {
            updateDataAvailabilityInfo(data);
        } else {
            console.warn('Failed to load data availability:', data.message);
        }
    } catch (error) {
        console.error('Error loading data availability:', error);
    }
}

// Update the UI with data availability information
function updateDataAvailabilityInfo(availabilityData) {
    // Update date inputs if we have actual data range
    if (availabilityData.hasData && availabilityData.oldestDate) {
        const formatDateForInput = (dateStr) => {
            if (!dateStr) return '';
            const date = new Date(dateStr);
            const year = date.getFullYear();
            const month = String(date.getMonth() + 1).padStart(2, '0');
            const day = String(date.getDate()).padStart(2, '0');
            const hours = String(date.getHours()).padStart(2, '0');
            const minutes = String(date.getMinutes()).padStart(2, '0');
            return `${year}-${month}-${day}T${hours}:${minutes}`;
        };
        
        const startDateInput = document.getElementById('startDate');
        const endDateInput = document.getElementById('endDate');
        
        // Update min/max based on actual available data
        startDateInput.min = formatDateForInput(availabilityData.oldestDate);
        endDateInput.max = formatDateForInput(availabilityData.newestDate);
        
        // Show data availability info to user
        const availabilityInfo = document.getElementById('dataAvailabilityInfo');
        if (availabilityInfo) {
            const oldestDate = new Date(availabilityData.oldestDate).toLocaleDateString();
            const recordCount = availabilityData.recordCount.toLocaleString();
            
            availabilityInfo.innerHTML = `
                <div class="availability-info">
                    <i class="fas fa-info-circle" style="color: var(--neon-green);"></i>
                    <span>Data available from <strong>${oldestDate}</strong> • ${recordCount} records • 18-month retention</span>
                </div>
            `;
            availabilityInfo.style.display = 'block';
        }
    } else {
        // Show no data message
        const availabilityInfo = document.getElementById('dataAvailabilityInfo');
        if (availabilityInfo) {
            availabilityInfo.innerHTML = `
                <div class="availability-info no-data">
                    <i class="fas fa-exclamation-triangle" style="color: #f59e0b;"></i>
                    <span>No historical data available. Generate sample data to get started.</span>
                </div>
            `;
            availabilityInfo.style.display = 'block';
        }
    }
}

// Check user authentication status
function checkUserAuthentication() {
    const savedUser = localStorage.getItem('skyPulseUser');
    const authHeader = sessionStorage.getItem('authorization');
    
    if (savedUser && authHeader) {
        try {
            currentUser = JSON.parse(savedUser);
            isLoggedIn = true;
            updateUserStatus();
        } catch (e) {
            console.warn('Invalid user data in localStorage');
        }
    }
    
    updateUserStatus();
}

// Update user status in navigation
function updateUserStatus() {
    const userStatusElement = document.getElementById('userStatus');
    if (isLoggedIn && currentUser) {
        userStatusElement.textContent = currentUser.username;
        userStatusElement.parentElement.onclick = () => showUserMenu();
    } else {
        userStatusElement.textContent = 'Login';
        userStatusElement.parentElement.onclick = () => redirectToLogin();
    }
}

// Redirect to login if needed
function checkAuthAndRedirect() {
    if (!isLoggedIn) {
        redirectToLogin();
    } else {
        showUserMenu();
    }
}

function redirectToLogin() {
    window.location.href = 'index.html';
}

// Load available cities for the dropdown
async function loadAvailableCities() {
    try {
        const response = await fetch(`${API_BASE_URL}/aqi/cities`);
        const data = await response.json();
        
        const citySelect = document.getElementById('citySelect');
        
        if (data.success && data.cities) {
            // Clear existing options except the first one
            citySelect.innerHTML = '<option value="">Select City...</option>';
            
            data.cities.forEach(city => {
                const option = document.createElement('option');
                option.value = city;
                option.textContent = city;
                citySelect.appendChild(option);
            });
        }
    } catch (error) {
        console.error('Error loading cities:', error);
        showNotification('Error loading cities list', 'error');
    }
}

// Main function to load and display analytics data using real-time OpenAQ data
async function loadAnalyticsData() {
    const city = document.getElementById('citySelect').value;
    const startDate = document.getElementById('startDate').value;
    const endDate = document.getElementById('endDate').value;
    
    // Validation
    if (!city) {
        showNotification('Please select a city', 'error');
        return;
    }
    
    if (!startDate || !endDate) {
        showNotification('Please select both start and end dates', 'error');
        return;
    }
    
    if (new Date(startDate) >= new Date(endDate)) {
        showNotification('Start date must be before end date', 'error');
        return;
    }
    
    // Check date range limit for real-time analytics (18 months max)
    const daysDiff = Math.ceil((new Date(endDate) - new Date(startDate)) / (1000 * 60 * 60 * 24));
    if (daysDiff > 540) {
        showNotification('Date range cannot exceed 18 months (540 days) for real-time analytics', 'error');
        return;
    }
    
    // Check if user is logged in for analytics access
    if (!isLoggedIn) {
        showNotification('Please login to access real-time analytics', 'error');
        redirectToLogin();
        return;
    }
    
    showLoading(true);
    hideAllSections();
    
    try {
        // Use new real-time analytics endpoint
        const response = await fetch(
            `${API_BASE_URL}/analytics/generate/${encodeURIComponent(city)}?startDate=${encodeURIComponent(startDate)}&endDate=${encodeURIComponent(endDate)}`,
            {
                headers: {
                    'Authorization': sessionStorage.getItem('authorization'),
                    'X-User-Id': sessionStorage.getItem('userId'),
                    'Content-Type': 'application/json'
                }
            }
        );
        
        const data = await response.json();
        
        if (data.success && data.statistics && data.charts) {
            analyticsData = {
                city: city,
                startDate: startDate,
                endDate: endDate,
                statistics: data.statistics,
                charts: data.charts
            };
            
            // Display analytics results
            displayRealTimeDataSummary(data);
            displayRealTimeCharts(data.charts);
            showDownloadSection();
            
            showNotification(`Real-time analytics generated for ${city} using OpenAQ data`, 'success');
        } else if (data.dataAvailable === false || data.suggestDataGeneration === true) {
            // Handle case where no historical data is available
            showNoDataMessage();
            showNotification(
                'No historical data available. Consider generating sample data using the "Generate Sample Data" option from the user menu.',
                'warning'
            );
        } else {
            showNoDataMessage();
            showNotification(data.message || 'No data available for the selected period', 'warning');
        }
        
    } catch (error) {
        console.error('Error loading real-time analytics data:', error);
        showNotification('Error loading real-time analytics data. Please try again.', 'error');
        showNoDataMessage();
    } finally {
        showLoading(false);
    }
}

// Display real-time analytics data summary
function displayRealTimeDataSummary(data) {
    if (!data.statistics) return;
    
    const stats = data.statistics;
    
    // Update summary values
    document.getElementById('totalRecords').textContent = stats.total_records || 0;
    document.getElementById('avgAQI').textContent = stats.avg_aqi || 0;
    document.getElementById('maxAQI').textContent = stats.max_aqi || 0;
    document.getElementById('minAQI').textContent = stats.min_aqi || 0;
    
    // Format time period
    const startDate = new Date(analyticsData.startDate);
    const endDate = new Date(analyticsData.endDate);
    const timePeriod = `${startDate.toLocaleDateString()} - ${endDate.toLocaleDateString()}`;
    document.getElementById('timePeriod').textContent = timePeriod;
    
    // Apply AQI colors to values
    document.getElementById('avgAQI').style.color = getAQIColor(stats.avg_aqi);
    document.getElementById('maxAQI').style.color = getAQIColor(stats.max_aqi);
    document.getElementById('minAQI').style.color = getAQIColor(stats.min_aqi);
    
    // Show data source information
    const sourceInfo = document.createElement('div');
    sourceInfo.className = 'data-source-info';
    sourceInfo.innerHTML = `
        <div class="info-badge">
            <i class="fas fa-satellite"></i> 
            <span>Real-time OpenAQ API Data</span>
        </div>
        <div class="analytics-info">
            <span><i class="fas fa-chart-line"></i> Python Analytics</span>
            <span><i class="fas fa-clock"></i> Generated: ${new Date().toLocaleString()}</span>
        </div>
    `;
    
    // Add to summary card if not already present
    const summaryCard = document.getElementById('summaryCard');
    if (!summaryCard.querySelector('.data-source-info')) {
        summaryCard.appendChild(sourceInfo);
    }
    
    // Show summary section
    summaryCard.style.display = 'block';
}

// Display real-time generated charts
function displayRealTimeCharts(charts) {
    if (!charts) return;
    
    // Display charts as base64 images
    if (charts.trendChart) {
        displayBase64Chart('aqiTrendChart', charts.trendChart, 'AQI Trend Over Time');
    }
    
    if (charts.barChart) {
        displayBase64Chart('pollutantsBarChart', charts.barChart, 'Average Pollutant Levels');
    }
    
    if (charts.pieChart) {
        displayBase64Chart('aqiPieChart', charts.pieChart, 'AQI Category Distribution');
    }
    
    if (charts.distributionChart) {
        displayBase64Chart('pollutionDistChart', charts.distributionChart, 'Pollution Level Distribution');
    }
    
    // Show charts container
    document.getElementById('chartsContainer').style.display = 'block';
}

// Helper function to display base64 chart as image
function displayBase64Chart(canvasId, base64Data, title) {
    const canvas = document.getElementById(canvasId);
    const container = canvas.parentElement;
    
    // Create image element to replace canvas
    const img = document.createElement('img');
    img.src = `data:image/png;base64,${base64Data}`;
    img.style.width = '100%';
    img.style.height = 'auto';
    img.style.maxHeight = '400px';
    img.alt = title;
    img.className = 'analytics-chart-image';
    
    // Add download button overlay
    const downloadBtn = document.createElement('button');
    downloadBtn.className = 'chart-download-btn';
    downloadBtn.innerHTML = '<i class="fas fa-download"></i>';
    downloadBtn.title = `Download ${title}`;
    downloadBtn.onclick = () => downloadBase64Image(base64Data, title);
    
    // Create chart wrapper
    const wrapper = document.createElement('div');
    wrapper.className = 'chart-wrapper';
    wrapper.style.position = 'relative';
    wrapper.appendChild(img);
    wrapper.appendChild(downloadBtn);
    
    // Replace canvas with image wrapper
    container.replaceChild(wrapper, canvas);
    
    // Update chart title if exists
    const titleElement = container.querySelector('.chart-title');
    if (titleElement) {
        titleElement.textContent = title;
    } else {
        const newTitle = document.createElement('h3');
        newTitle.className = 'chart-title';
        newTitle.textContent = title;
        container.insertBefore(newTitle, wrapper);
    }
}

// Download base64 image
function downloadBase64Image(base64Data, filename) {
    const link = document.createElement('a');
    link.href = `data:image/png;base64,${base64Data}`;
    link.download = `${filename.replace(/\s+/g, '-').toLowerCase()}-${analyticsData.city}-${new Date().toISOString().split('T')[0]}.png`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showNotification('Chart downloaded successfully!', 'success');
}

// Get AQI color helper function
function getAQIColor(aqi) {
    if (aqi <= 50) return '#00ff88';      // Good
    else if (aqi <= 100) return '#ffff00';  // Moderate
    else if (aqi <= 150) return '#ff8800';  // Unhealthy for Sensitive Groups
    else if (aqi <= 200) return '#ff0000';  // Unhealthy
    else if (aqi <= 300) return '#8800ff';  // Very Unhealthy
    else return '#880000';                   // Hazardous
}

// Create all analytics charts
function createAllCharts() {
    if (!analyticsData) return;
    
    // Destroy existing charts
    Object.values(activeCharts).forEach(chart => {
        if (chart) chart.destroy();
    });
    activeCharts = {};
    
    // Create different types of charts
    createAQITrendChart();
    createPollutantsBarChart();
    createAQICategoriesPieChart();
    createPollutionDistributionChart();
    
    // Show charts container
    document.getElementById('chartsContainer').style.display = 'block';
}

// Create AQI trend over time chart
function createAQITrendChart() {
    const ctx = document.getElementById('aqiTrendChart').getContext('2d');
    
    const chartData = analyticsData.data.map(item => ({
        x: new Date(item.timestamp),
        y: item.aqiValue,
        pm25: item.pm25,
        pm10: item.pm10,
        no2: item.no2,
        so2: item.so2,
        co: item.co,
        o3: item.o3
    }));
    
    // Sort by timestamp
    chartData.sort((a, b) => a.x - b.x);
    
    activeCharts.aqiTrend = new Chart(ctx, {
        type: 'line',
        data: {
            datasets: [{
                label: 'AQI Value',
                data: chartData,
                borderColor: 'var(--neon-blue)',
                backgroundColor: 'rgba(84, 160, 255, 0.1)',
                borderWidth: 3,
                fill: true,
                tension: 0.4,
                pointBackgroundColor: function(context) {
                    return ChartManager.getAQIColor(context.parsed.y);
                },
                pointBorderColor: '#ffffff',
                pointBorderWidth: 2,
                pointRadius: 4,
                pointHoverRadius: 8
            }]
        },
        options: getTimeSeriesChartOptions(`AQI Trend - ${analyticsData.city}`)
    });
}

// Create pollutants bar chart (average values)
function createPollutantsBarChart() {
    const ctx = document.getElementById('pollutantsBarChart').getContext('2d');
    
    // Calculate average values for each pollutant
    const validData = analyticsData.data.filter(item => 
        item.pm25 !== null || item.pm10 !== null || item.no2 !== null || 
        item.so2 !== null || item.co !== null || item.o3 !== null
    );
    
    const averages = {
        'PM2.5': calculateAverage(validData, 'pm25'),
        'PM10': calculateAverage(validData, 'pm10'),
        'NO2': calculateAverage(validData, 'no2'),
        'SO2': calculateAverage(validData, 'so2'),
        'CO': calculateAverage(validData, 'co'),
        'O3': calculateAverage(validData, 'o3')
    };
    
    const labels = Object.keys(averages);
    const data = Object.values(averages);
    
    activeCharts.pollutantsBar = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [{
                label: 'Average Concentration',
                data: data,
                backgroundColor: [
                    'rgba(84, 160, 255, 0.8)',   // PM2.5
                    'rgba(95, 39, 205, 0.8)',    // PM10
                    'rgba(255, 159, 243, 0.8)',  // NO2
                    'rgba(255, 107, 107, 0.8)',  // SO2
                    'rgba(16, 172, 132, 0.8)',   // CO
                    'rgba(255, 234, 167, 0.8)'   // O3
                ],
                borderColor: [
                    'rgba(84, 160, 255, 1)',
                    'rgba(95, 39, 205, 1)',
                    'rgba(255, 159, 243, 1)',
                    'rgba(255, 107, 107, 1)',
                    'rgba(16, 172, 132, 1)',
                    'rgba(255, 234, 167, 1)'
                ],
                borderWidth: 2,
                borderRadius: 8,
                borderSkipped: false
            }]
        },
        options: getBarChartOptions(`Average Pollutant Levels - ${analyticsData.city}`)
    });
}

// Create AQI categories pie chart
function createAQICategoriesPieChart() {
    const ctx = document.getElementById('aqiPieChart').getContext('2d');
    
    // Categorize AQI values
    const categories = {
        'Good (0-50)': 0,
        'Moderate (51-100)': 0,
        'Unhealthy for Sensitive (101-150)': 0,
        'Unhealthy (151-200)': 0,
        'Very Unhealthy (201-300)': 0,
        'Hazardous (301+)': 0
    };
    
    analyticsData.data.forEach(item => {
        const aqi = item.aqiValue;
        if (aqi <= 50) categories['Good (0-50)']++;
        else if (aqi <= 100) categories['Moderate (51-100)']++;
        else if (aqi <= 150) categories['Unhealthy for Sensitive (101-150)']++;
        else if (aqi <= 200) categories['Unhealthy (151-200)']++;
        else if (aqi <= 300) categories['Very Unhealthy (201-300)']++;
        else categories['Hazardous (301+)']++;
    });
    
    // Filter out categories with zero values
    const labels = [];
    const data = [];
    const backgroundColors = [];
    
    Object.entries(categories).forEach(([category, count]) => {
        if (count > 0) {
            labels.push(category);
            data.push(count);
            // Use corresponding AQI colors
            if (category.includes('Good')) backgroundColors.push('#00ff88');
            else if (category.includes('Moderate')) backgroundColors.push('#ffff00');
            else if (category.includes('Sensitive')) backgroundColors.push('#ff8800');
            else if (category.includes('Unhealthy (151')) backgroundColors.push('#ff0000');
            else if (category.includes('Very Unhealthy')) backgroundColors.push('#8800ff');
            else backgroundColors.push('#880000');
        }
    });
    
    activeCharts.aqiPie = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: labels,
            datasets: [{
                data: data,
                backgroundColor: backgroundColors,
                borderColor: '#ffffff',
                borderWidth: 2,
                hoverBorderWidth: 4
            }]
        },
        options: getPieChartOptions(`AQI Category Distribution - ${analyticsData.city}`)
    });
}

// Create pollution level distribution chart
function createPollutionDistributionChart() {
    const ctx = document.getElementById('pollutionDistChart').getContext('2d');
    
    // Create ranges for pollution levels
    const ranges = {
        'Very Low (0-50)': 0,
        'Low (51-100)': 0,
        'Medium (101-150)': 0,
        'High (151-200)': 0,
        'Very High (201-300)': 0,
        'Extreme (301+)': 0
    };
    
    analyticsData.data.forEach(item => {
        const aqi = item.aqiValue;
        if (aqi <= 50) ranges['Very Low (0-50)']++;
        else if (aqi <= 100) ranges['Low (51-100)']++;
        else if (aqi <= 150) ranges['Medium (101-150)']++;
        else if (aqi <= 200) ranges['High (151-200)']++;
        else if (aqi <= 300) ranges['Very High (201-300)']++;
        else ranges['Extreme (301+)']++;
    });
    
    const labels = Object.keys(ranges);
    const data = Object.values(ranges);
    
    activeCharts.pollutionDist = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [{
                label: 'Number of Readings',
                data: data,
                backgroundColor: [
                    'rgba(0, 255, 136, 0.8)',    // Very Low
                    'rgba(255, 255, 0, 0.8)',     // Low
                    'rgba(255, 136, 0, 0.8)',     // Medium
                    'rgba(255, 0, 0, 0.8)',       // High
                    'rgba(136, 0, 255, 0.8)',     // Very High
                    'rgba(136, 0, 0, 0.8)'        // Extreme
                ],
                borderColor: '#ffffff',
                borderWidth: 2,
                borderRadius: 8
            }]
        },
        options: getBarChartOptions(`Pollution Level Distribution - ${analyticsData.city}`)
    });
}

// Chart options helpers
function getTimeSeriesChartOptions(title) {
    return {
        responsive: true,
        maintainAspectRatio: false,
        interaction: {
            intersect: false,
            mode: 'index'
        },
        scales: {
            x: {
                type: 'time',
                time: {
                    displayFormats: {
                        minute: 'HH:mm',
                        hour: 'MMM dd HH:mm',
                        day: 'MMM dd',
                        week: 'MMM dd',
                        month: 'MMM yyyy'
                    }
                },
                grid: {
                    color: 'rgba(255, 255, 255, 0.1)'
                },
                ticks: {
                    color: 'rgba(255, 255, 255, 0.7)'
                }
            },
            y: {
                beginAtZero: true,
                max: 500,
                grid: {
                    color: 'rgba(255, 255, 255, 0.1)'
                },
                ticks: {
                    color: 'rgba(255, 255, 255, 0.7)',
                    callback: function(value) {
                        return value + ' AQI';
                    }
                }
            }
        },
        plugins: {
            title: {
                display: true,
                text: title,
                color: 'rgba(255, 255, 255, 0.9)',
                font: { size: 16, weight: 'bold' }
            },
            legend: {
                labels: {
                    color: 'rgba(255, 255, 255, 0.9)'
                }
            },
            tooltip: {
                backgroundColor: 'rgba(0, 0, 0, 0.9)',
                titleColor: 'white',
                bodyColor: 'white'
            }
        }
    };
}

function getBarChartOptions(title) {
    return {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
            y: {
                beginAtZero: true,
                grid: {
                    color: 'rgba(255, 255, 255, 0.1)'
                },
                ticks: {
                    color: 'rgba(255, 255, 255, 0.7)'
                }
            },
            x: {
                grid: {
                    color: 'rgba(255, 255, 255, 0.1)'
                },
                ticks: {
                    color: 'rgba(255, 255, 255, 0.7)'
                }
            }
        },
        plugins: {
            title: {
                display: true,
                text: title,
                color: 'rgba(255, 255, 255, 0.9)',
                font: { size: 16, weight: 'bold' }
            },
            legend: {
                labels: {
                    color: 'rgba(255, 255, 255, 0.9)'
                }
            },
            tooltip: {
                backgroundColor: 'rgba(0, 0, 0, 0.9)',
                titleColor: 'white',
                bodyColor: 'white'
            }
        }
    };
}

function getPieChartOptions(title) {
    return {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
            title: {
                display: true,
                text: title,
                color: 'rgba(255, 255, 255, 0.9)',
                font: { size: 16, weight: 'bold' }
            },
            legend: {
                position: 'bottom',
                labels: {
                    color: 'rgba(255, 255, 255, 0.9)',
                    padding: 20,
                    usePointStyle: true
                }
            },
            tooltip: {
                backgroundColor: 'rgba(0, 0, 0, 0.9)',
                titleColor: 'white',
                bodyColor: 'white',
                callbacks: {
                    label: function(context) {
                        const total = context.dataset.data.reduce((a, b) => a + b, 0);
                        const percentage = ((context.parsed * 100) / total).toFixed(1);
                        return `${context.label}: ${context.parsed} (${percentage}%)`;
                    }
                }
            }
        }
    };
}

// Utility functions
function calculateAverage(data, field) {
    const validValues = data.map(item => item[field]).filter(val => val !== null && val !== undefined);
    if (validValues.length === 0) return 0;
    return Math.round((validValues.reduce((sum, val) => sum + val, 0) / validValues.length) * 100) / 100;
}

function showLoading(show) {
    document.getElementById('loading').style.display = show ? 'block' : 'none';
}

function hideAllSections() {
    document.getElementById('summaryCard').style.display = 'none';
    document.getElementById('chartsContainer').style.display = 'none';
    document.getElementById('downloadSection').style.display = 'none';
    document.getElementById('noDataMessage').style.display = 'none';
}

function showDownloadSection() {
    document.getElementById('downloadSection').style.display = 'block';
}

function showNoDataMessage() {
    const noDataElement = document.getElementById('noDataMessage');
    const noDataContent = noDataElement.querySelector('.no-data-content');
    
    // Update message to reflect real-time analytics approach
    noDataContent.innerHTML = `
        <i class="fas fa-satellite fa-3x" style="color: var(--text-secondary); margin-bottom: 1rem;"></i>
        <h3>No Real-time Data Available</h3>
        <p>Unable to fetch real-time air quality data for the selected city and time period.</p>
        <p>This could be because:</p>
        <ul style="text-align: left; margin: 1rem 0;">
            <li>OpenAQ API doesn't have data for this city/period</li>
            <li>The city name doesn't match OpenAQ records</li>
            <li>Network connectivity issues</li>
            <li>The date range is too recent (data may have delays)</li>
        </ul>
        <div style="margin-top: 2rem;">
            <button class="btn-primary" onclick="loadAvailableCities()" style="margin-right: 1rem;">
                <i class="fas fa-refresh"></i> Refresh Cities
            </button>
            <button class="btn-secondary" onclick="resetFilters()">
                <i class="fas fa-undo"></i> Reset Filters
            </button>
        </div>
        <p style="margin-top: 1rem; font-size: 0.9rem; color: var(--text-secondary);">
            <i class="fas fa-info-circle"></i> Real-time analytics fetch fresh data from OpenAQ API
        </p>
        <div class="suggested-cities" style="margin-top: 1rem;">
            <p><strong>Try these popular cities:</strong></p>
            <div class="city-suggestions">
                <button class="city-suggestion-btn" onclick="selectSuggestedCity('Delhi')">Delhi</button>
                <button class="city-suggestion-btn" onclick="selectSuggestedCity('New York')">New York</button>
                <button class="city-suggestion-btn" onclick="selectSuggestedCity('London')">London</button>
                <button class="city-suggestion-btn" onclick="selectSuggestedCity('Beijing')">Beijing</button>
            </div>
        </div>
    `;
    
    noDataElement.style.display = 'block';
}

function selectSuggestedCity(cityName) {
    document.getElementById('citySelect').value = cityName;
    // Set default date range to last 7 days for suggested cities
    setDateRange(7);
    showNotification(`Selected ${cityName}. Click "Load Analytics" to fetch real-time data.`, 'info');
}

function resetFilters() {
    document.getElementById('citySelect').value = '';
    initializeDateInputs();
    hideAllSections();
}

// Export individual chart as image
function exportChart(chartId, filename) {
    const chartKey = chartId.replace('Chart', '').replace('aqi', 'aqi').replace('Trend', 'Trend').replace('pollutants', 'pollutants').replace('Bar', 'Bar').replace('Pie', 'Pie').replace('pollution', 'pollution').replace('Dist', 'Dist');
    
    let chart = null;
    if (chartId === 'aqiTrendChart') chart = activeCharts.aqiTrend;
    else if (chartId === 'pollutantsBarChart') chart = activeCharts.pollutantsBar;
    else if (chartId === 'aqiPieChart') chart = activeCharts.aqiPie;
    else if (chartId === 'pollutionDistChart') chart = activeCharts.pollutionDist;
    
    if (chart) {
        const url = chart.toBase64Image();
        const link = document.createElement('a');
        link.download = `${filename}-${analyticsData.city}-${new Date().toISOString().split('T')[0]}.png`;
        link.href = url;
        link.click();
        showNotification('Chart exported successfully!', 'success');
    }
}

// Download all charts as images
function downloadAllCharts() {
    if (!analyticsData) {
        showNotification('No data available to export', 'error');
        return;
    }
    
    const charts = [
        { id: 'aqiTrendChart', name: 'aqi-trend' },
        { id: 'pollutantsBarChart', name: 'pollutants-bar' },
        { id: 'aqiPieChart', name: 'aqi-categories' },
        { id: 'pollutionDistChart', name: 'pollution-distribution' }
    ];
    
    charts.forEach((chart, index) => {
        setTimeout(() => {
            exportChart(chart.id, chart.name);
        }, index * 500); // Stagger downloads
    });
    
    showNotification('All charts will be downloaded shortly', 'success');
}

// Download PDF report with real-time analytics and charts
async function downloadPDFReport() {
    if (!isLoggedIn) {
        showNotification('Please login to download PDF reports', 'error');
        redirectToLogin();
        return;
    }
    
    if (!analyticsData) {
        showNotification('No data available to export', 'error');
        return;
    }
    
    try {
        showNotification('Generating enhanced PDF report with real-time analytics...', 'info');
        
        const response = await fetch(
            `${API_BASE_URL}/analytics/pdf/${encodeURIComponent(analyticsData.city)}?startDate=${encodeURIComponent(analyticsData.startDate)}&endDate=${encodeURIComponent(analyticsData.endDate)}`,
            {
                method: 'GET',
                headers: {
                    'Authorization': sessionStorage.getItem('authorization'),
                    'X-User-Id': sessionStorage.getItem('userId')
                }
            }
        );
        
        if (response.ok) {
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `real-time-analytics-${analyticsData.city}-${new Date().toISOString().split('T')[0]}.pdf`;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            window.URL.revokeObjectURL(url);
            
            showNotification('Real-time analytics PDF report downloaded successfully!', 'success');
        } else {
            throw new Error('PDF generation failed');
        }
        
    } catch (error) {
        console.error('Download error:', error);
        showNotification('Failed to generate PDF report. Please try again.', 'error');
    }
}

// Download CSV data
async function downloadCSVData() {
    if (!isLoggedIn) {
        showNotification('Please login to download CSV data', 'error');
        redirectToLogin();
        return;
    }
    
    if (!analyticsData) {
        showNotification('No data available to export', 'error');
        return;
    }
    
    try {
        showNotification('Generating CSV data...', 'info');
        
        const response = await fetch(
            `${API_BASE_URL}/export/csv?city=${encodeURIComponent(analyticsData.city)}&startDate=${encodeURIComponent(analyticsData.startDate)}&endDate=${encodeURIComponent(analyticsData.endDate)}`,
            {
                method: 'GET',
                headers: {
                    'X-User-Id': sessionStorage.getItem('userId')
                }
            }
        );
        
        if (response.ok) {
            const csvData = await response.text();
            const blob = new Blob([csvData], { type: 'text/csv' });
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `air-quality-data-${analyticsData.city}-${new Date().toISOString().split('T')[0]}.csv`;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            window.URL.revokeObjectURL(url);
            
            showNotification('CSV data downloaded successfully!', 'success');
        } else {
            throw new Error('CSV generation failed');
        }
        
    } catch (error) {
        console.error('Download error:', error);
        showNotification('Failed to generate CSV data. Please try again.', 'error');
    }
}

// Show user menu (simplified version)
function showUserMenu() {
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
            <small style="color: var(--text-secondary);">${currentUser.email || ''}</small>
        </div>
        <a href="index.html" style="display: block; padding: 0.5rem 0; color: var(--text-primary); text-decoration: none;">
            <i class="fas fa-home"></i> Home
        </a>
        <a href="#" onclick="logout()" style="display: block; padding: 0.5rem 0; color: var(--text-primary); text-decoration: none;">
            <i class="fas fa-sign-out-alt"></i> Logout
        </a>
    `;
    
    // Remove existing menu if any
    const existingMenu = document.querySelector('.user-menu');
    if (existingMenu) existingMenu.remove();
    
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

// Logout function
function logout() {
    localStorage.removeItem('skyPulseUser');
    sessionStorage.removeItem('authorization');
    sessionStorage.removeItem('userId');
    window.location.href = 'index.html';
}

// Check data availability and suggest seeding if needed
async function checkDataAvailability() {
    try {
        const response = await fetch(`${API_BASE_URL}/admin/database-status`);
        const data = await response.json();
        
        if (data.success && data.totalRecords < 1000) {
            // Very few records, suggest seeding
            showNotification('Very limited historical data available. Consider generating sample data for better analytics experience.', 'info');
        }
    } catch (error) {
        console.warn('Could not check data availability:', error);
    }
}

// Seed historical data
async function seedHistoricalData() {
    if (!confirm('This will generate 3 years of sample historical data for all cities. This process may take a few minutes. Continue?')) {
        return;
    }
    
    try {
        showNotification('Starting historical data generation... This may take a few minutes.', 'info');
        
        const response = await fetch(`${API_BASE_URL}/admin/seed-historical-data?years=3`, {
            method: 'POST'
        });
        
        const data = await response.json();
        
        if (data.success) {
            showNotification('Historical data generation started successfully! Please wait a few minutes and then try loading analytics again.', 'success');
            
            // Hide the no data message and show loading
            document.getElementById('noDataMessage').style.display = 'none';
            showLoading(true);
            
            // Check progress periodically
            let checkCount = 0;
            const progressCheck = setInterval(async () => {
                checkCount++;
                try {
                    const statusResponse = await fetch(`${API_BASE_URL}/admin/database-status`);
                    const statusData = await statusResponse.json();
                    
                    if (statusData.success && statusData.totalRecords > 1000) {
                        clearInterval(progressCheck);
                        showLoading(false);
                        showNotification('Historical data generation completed! You can now load analytics.', 'success');
                    } else if (checkCount > 20) { // Stop checking after 10 minutes
                        clearInterval(progressCheck);
                        showLoading(false);
                        showNotification('Data generation is taking longer than expected. Please try again later.', 'warning');
                    }
                } catch (error) {
                    // Continue checking
                }
            }, 30000); // Check every 30 seconds
            
        } else {
            showNotification(data.message || 'Failed to start historical data generation', 'error');
        }
        
    } catch (error) {
        console.error('Error seeding historical data:', error);
        showNotification('Error starting historical data generation. Please try again.', 'error');
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

// Optimized Analytics Dashboard Manager (progressive enhancement)
class OptimizedAnalyticsManager {
    constructor() {
        this.apiBaseUrl = 'http://localhost:8080/api';
        this.authToken = sessionStorage.getItem('authorization');
        this.userId = sessionStorage.getItem('userId');
        this.isAuthenticated = !!this.authToken && !!this.userId;
        this.cache = new Map();
        this.cacheTimeout = 5 * 60 * 1000; // 5 minutes
        this.requestQueue = new Map();
        this.maxConcurrentRequests = 3;
        this.activeRequests = 0;
        this.chartInstances = {};
        this.init();
    }
    async init() {
        if (!this.isAuthenticated) {
            this.showAuthError();
            return;
        }
        this.showLoadingState();
        await this.loadDashboard();
        this.setupEventListeners();
        this.startBackgroundRefresh();
    }
    showAuthError() {
        showNotification('Authentication required. Please login to continue.', 'error');
        setTimeout(() => {
            window.location.href = 'index.html';
        }, 3000);
    }
    showLoadingState() {
        showLoading(true);
    }
    async loadDashboard() {
        // Load initial data and charts for the dashboard
        await this.loadData();
        this.createCharts();
    }
    async loadData() {
        const city = document.getElementById('citySelect').value;
        const startDate = document.getElementById('startDate').value;
        const endDate = document.getElementById('endDate').value;
        
        // Validation
        if (!city) {
            showNotification('Please select a city', 'error');
            return;
        }
        
        if (!startDate || !endDate) {
            showNotification('Please select both start and end dates', 'error');
            return;
        }
        
        if (new Date(startDate) >= new Date(endDate)) {
            showNotification('Start date must be before end date', 'error');
            return;
        }
        
        // Check date range limit for real-time analytics (18 months max)
        const daysDiff = Math.ceil((new Date(endDate) - new Date(startDate)) / (1000 * 60 * 60 * 24));
        if (daysDiff > 540) {
            showNotification('Date range cannot exceed 18 months (540 days) for real-time analytics', 'error');
            return;
        }
        
        // Check if user is logged in for analytics access
        if (!isLoggedIn) {
            showNotification('Please login to access real-time analytics', 'error');
            redirectToLogin();
            return;
        }
        
        const cacheKey = `${city}|${startDate}|${endDate}`;
        const cachedData = this.cache.get(cacheKey);
        if (cachedData) {
            // Use cached data if available and not expired
            this.updateDashboardData(cachedData);
        } else {
            // Fetch new data from the server
            try {
                const response = await fetch(
                    `${API_BASE_URL}/analytics/generate/${encodeURIComponent(city)}?startDate=${encodeURIComponent(startDate)}&endDate=${encodeURIComponent(endDate)}`,
                    {
                        headers: {
                            'Authorization': sessionStorage.getItem('authorization'),
                            'X-User-Id': sessionStorage.getItem('userId'),
                            'Content-Type': 'application/json'
                        }
                    }
                );
                
                const data = await response.json();
                
                if (data.success && data.statistics && data.charts) {
                    // Cache the data with an expiration time
                    this.cache.set(cacheKey, {
                        data: data,
                        timestamp: Date.now()
                    });
                    
                    this.updateDashboardData(data);
                } else {
                    showNotification(data.message || 'No data available for the selected period', 'warning');
                }
                
            } catch (error) {
                console.error('Error loading real-time analytics data:', error);
                showNotification('Error loading real-time analytics data. Please try again.', 'error');
            } finally {
                this.hideLoadingState();
            }
        }
    }
    updateDashboardData(data) {
        analyticsData = {
            city: data.city,
            startDate: data.startDate,
            endDate: data.endDate,
            statistics: data.statistics,
            charts: data.charts
        };
        
        // Update summary values
        document.getElementById('totalRecords').textContent = data.statistics.total_records || 0;
        document.getElementById('avgAQI').textContent = data.statistics.avg_aqi || 0;
        document.getElementById('maxAQI').textContent = data.statistics.max_aqi || 0;
        document.getElementById('minAQI').textContent = data.statistics.min_aqi || 0;
        
        // Format time period
        const startDate = new Date(data.startDate);
        const endDate = new Date(data.endDate);
        const timePeriod = `${startDate.toLocaleDateString()} - ${endDate.toLocaleDateString()}`;
        document.getElementById('timePeriod').textContent = timePeriod;
        
        // Apply AQI colors to values
        document.getElementById('avgAQI').style.color = getAQIColor(data.statistics.avg_aqi);
        document.getElementById('maxAQI').style.color = getAQIColor(data.statistics.max_aqi);
        document.getElementById('minAQI').style.color = getAQIColor(data.statistics.min_aqi);
        
        this.createCharts(data.charts);
        
        showNotification(`Real-time analytics generated for ${data.city} using OpenAQ data`, 'success');
    }
    createCharts(chartsData) {
        // Destroy existing charts
        Object.values(activeCharts).forEach(chart => {
            if (chart) chart.destroy();
        });
        activeCharts = {};
        
        // Create different types of charts
        this.createAQITrendChart(chartsData.trendChart);
        this.createPollutantsBarChart(chartsData.barChart);
        this.createAQICategoriesPieChart(chartsData.pieChart);
        this.createPollutionDistributionChart(chartsData.distributionChart);
        
        // Show charts container
        document.getElementById('chartsContainer').style.display = 'block';
    }
    createAQITrendChart(chartData) {
        const ctx = document.getElementById('aqiTrendChart').getContext('2d');
        
        // Use provided chart data
        activeCharts.aqiTrend = new Chart(ctx, {
            type: 'line',
            data: {
                datasets: [{
                    label: 'AQI Value',
                    data: chartData,
                    borderColor: 'var(--neon-blue)',
                    backgroundColor: 'rgba(84, 160, 255, 0.1)',
                    borderWidth: 3,
                    fill: true,
                    tension: 0.4,
                    pointBackgroundColor: function(context) {
                        return ChartManager.getAQIColor(context.parsed.y);
                    },
                    pointBorderColor: '#ffffff',
                    pointBorderWidth: 2,
                    pointRadius: 4,
                    pointHoverRadius: 8
                }]
            },
            options: getTimeSeriesChartOptions(`AQI Trend - ${analyticsData.city}`)
        });
    }
    createPollutantsBarChart(chartData) {
        const ctx = document.getElementById('pollutantsBarChart').getContext('2d');
        
        // Use provided chart data
        const labels = Object.keys(chartData);
        const data = Object.values(chartData);
        
        activeCharts.pollutantsBar = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [{
                    label: 'Average Concentration',
                    data: data,
                    backgroundColor: [
                        'rgba(84, 160, 255, 0.8)',   // PM2.5
                        'rgba(95, 39, 205, 0.8)',    // PM10
                        'rgba(255, 159, 243, 0.8)',  // NO2
                        'rgba(255, 107, 107, 0.8)',  // SO2
                        'rgba(16, 172, 132, 0.8)',   // CO
                        'rgba(255, 234, 167, 0.8)'   // O3
                    ],
                    borderColor: [
                        'rgba(84, 160, 255, 1)',
                        'rgba(95, 39, 205, 1)',
                        'rgba(255, 159, 243, 1)',
                        'rgba(255, 107, 107, 1)',
                        'rgba(16, 172, 132, 1)',
                        'rgba(255, 234, 167, 1)'
                    ],
                    borderWidth: 2,
                    borderRadius: 8,
                    borderSkipped: false
                }]
            },
            options: getBarChartOptions(`Average Pollutant Levels - ${analyticsData.city}`)
        });
    }
    createAQICategoriesPieChart(chartData) {
        const ctx = document.getElementById('aqiPieChart').getContext('2d');
        
        // Use provided chart data
        const labels = Object.keys(chartData);
        const data = Object.values(chartData);
        const backgroundColors = labels.map(label => {
            if (label.includes('Good')) return '#00ff88';
            else if (label.includes('Moderate')) return '#ffff00';
            else if (label.includes('Sensitive')) return '#ff8800';
            else if (label.includes('Unhealthy (151')) return '#ff0000';
            else if (label.includes('Very Unhealthy')) return '#8800ff';
            else return '#880000';
        });
        
        activeCharts.aqiPie = new Chart(ctx, {
            type: 'doughnut',
            data: {
                labels: labels,
                datasets: [{
                    data: data,
                    backgroundColor: backgroundColors,
                    borderColor: '#ffffff',
                    borderWidth: 2,
                    hoverBorderWidth: 4
                }]
            },
            options: getPieChartOptions(`AQI Category Distribution - ${analyticsData.city}`)
        });
    }
    createPollutionDistributionChart(chartData) {
        const ctx = document.getElementById('pollutionDistChart').getContext('2d');
        
        // Use provided chart data
        const labels = Object.keys(chartData);
        const data = Object.values(chartData);
        
        activeCharts.pollutionDist = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [{
                    label: 'Number of Readings',
                    data: data,
                    backgroundColor: [
                        'rgba(0, 255, 136, 0.8)',    // Very Low
                        'rgba(255, 255, 0, 0.8)',     // Low
                        'rgba(255, 136, 0, 0.8)',     // Medium
                        'rgba(255, 0, 0, 0.8)',       // High
                        'rgba(136, 0, 255, 0.8)',     // Very High
                        'rgba(136, 0, 0, 0.8)'        // Extreme
                    ],
                    borderColor: '#ffffff',
                    borderWidth: 2,
                    borderRadius: 8
                }]
            },
            options: getBarChartOptions(`Pollution Level Distribution - ${analyticsData.city}`)
        });
    }
    setupEventListeners() {
        // Setup any required event listeners for the dashboard
        const citySelect = document.getElementById('citySelect');
        if (citySelect) {
            citySelect.addEventListener('change', () => {
                this.loadData();
            });
        }
        
        const dateInputs = document.querySelectorAll('input[type="datetime-local"]');
        dateInputs.forEach(input => {
            input.addEventListener('change', () => {
                this.loadData();
            });
        });
    }
    startBackgroundRefresh() {
        // Periodically refresh the data in the background
        setInterval(() => {
            if (this.isAuthenticated) {
                this.loadData();
            }
        }, 5 * 60 * 1000); // Refresh every 5 minutes
    }
    cleanup() {
        // Cleanup resources and intervals
        Object.values(activeCharts).forEach(chart => {
            if (chart) chart.destroy();
        });
        activeCharts = {};
        
        this.cache.clear();
        
        // Remove event listeners, if any
    }
}

// Initialize optimized analytics when DOM is ready
if (!window.analyticsManager) {
    document.addEventListener('DOMContentLoaded', () => {
        window.optimizedAnalyticsManager = new OptimizedAnalyticsManager();
    });
    window.addEventListener('beforeunload', () => {
        if (window.optimizedAnalyticsManager) {
            window.optimizedAnalyticsManager.cleanup();
        }
    });
}
