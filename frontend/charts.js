// charts.js - Historical Data Visualization
// Handles all Chart.js functionality for SkyPulse

// Global chart instance
let currentChart = null;

// Initialize Chart.js with default configuration
Chart.defaults.font.family = "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif";
Chart.defaults.font.size = 12;
Chart.defaults.color = 'rgba(255, 255, 255, 0.7)';

// AQI Color mapping for chart data points
const AQI_COLORS = {
    good: '#00ff88',           // 0-50
    moderate: '#ffff00',       // 51-100
    unhealthySensitive: '#ff8800', // 101-150
    unhealthy: '#ff0000',      // 151-200
    veryUnhealthy: '#8800ff',  // 201-300
    hazardous: '#880000'       // 301+
};

// Get AQI color based on value
function getAQIColor(aqi) {
    if (aqi <= 50) return AQI_COLORS.good;
    if (aqi <= 100) return AQI_COLORS.moderate;
    if (aqi <= 150) return AQI_COLORS.unhealthySensitive;
    if (aqi <= 200) return AQI_COLORS.unhealthy;
    if (aqi <= 300) return AQI_COLORS.veryUnhealthy;
    return AQI_COLORS.hazardous;
}

// Get AQI category name
function getAQICategory(aqi) {
    if (aqi <= 50) return 'Good';
    if (aqi <= 100) return 'Moderate';
    if (aqi <= 150) return 'Unhealthy for Sensitive Groups';
    if (aqi <= 200) return 'Unhealthy';
    if (aqi <= 300) return 'Very Unhealthy';
    return 'Hazardous';
}

// Create historical data chart
function createHistoricalChart(data, cityName) {
    const ctx = document.getElementById('aqiChart').getContext('2d');
    
    // Destroy existing chart if it exists
    if (currentChart) {
        currentChart.destroy();
        currentChart = null;
    }
    
    // Prepare data for Chart.js
    const chartData = data.map(item => ({
        x: new Date(item.timestamp),
        y: item.aqiValue,
        pm25: item.pm25,
        pm10: item.pm10,
        no2: item.no2,
        so2: item.so2,
        co: item.co,
        o3: item.o3
    }));
    
    // Sort data by timestamp
    chartData.sort((a, b) => a.x - b.x);
    
    currentChart = new Chart(ctx, {
        type: 'line',
        data: {
            datasets: [{
                label: 'AQI Value',
                data: chartData,
                borderColor: '#54a0ff',
                backgroundColor: 'rgba(84, 160, 255, 0.1)',
                borderWidth: 3,
                fill: true,
                tension: 0.4,
                pointBackgroundColor: function(context) {
                    const aqi = context.parsed.y;
                    return getAQIColor(aqi);
                },
                pointBorderColor: '#ffffff',
                pointBorderWidth: 2,
                pointRadius: 5,
                pointHoverRadius: 8,
                pointHoverBackgroundColor: function(context) {
                    const aqi = context.parsed.y;
                    return getAQIColor(aqi);
                },
                pointHoverBorderColor: '#ffffff',
                pointHoverBorderWidth: 3
            }]
        },
        options: {
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
                        },
                        tooltipFormat: 'MMM dd, yyyy HH:mm'
                    },
                    grid: {
                        color: 'rgba(255, 255, 255, 0.1)',
                        borderColor: 'rgba(255, 255, 255, 0.2)'
                    },
                    ticks: {
                        color: 'rgba(255, 255, 255, 0.7)',
                        maxTicksLimit: 8
                    },
                    border: {
                        color: 'rgba(255, 255, 255, 0.2)'
                    }
                },
                y: {
                    beginAtZero: true,
                    max: 500,
                    grid: {
                        color: 'rgba(255, 255, 255, 0.1)',
                        borderColor: 'rgba(255, 255, 255, 0.2)'
                    },
                    ticks: {
                        color: 'rgba(255, 255, 255, 0.7)',
                        callback: function(value) {
                            return value + ' AQI';
                        }
                    },
                    border: {
                        color: 'rgba(255, 255, 255, 0.2)'
                    }
                }
            },
            plugins: {
                title: {
                    display: true,
                    text: `Air Quality History - ${cityName}`,
                    color: 'rgba(255, 255, 255, 0.9)',
                    font: {
                        size: 18,
                        weight: 'bold'
                    },
                    padding: {
                        top: 10,
                        bottom: 20
                    }
                },
                legend: {
                    display: true,
                    position: 'top',
                    align: 'end',
                    labels: {
                        color: 'rgba(255, 255, 255, 0.9)',
                        usePointStyle: true,
                        pointStyle: 'circle',
                        padding: 20
                    }
                },
                tooltip: {
                    backgroundColor: 'rgba(0, 0, 0, 0.9)',
                    titleColor: 'white',
                    bodyColor: 'white',
                    borderColor: 'rgba(255, 255, 255, 0.3)',
                    borderWidth: 1,
                    cornerRadius: 10,
                    displayColors: true,
                    callbacks: {
                        title: function(context) {
                            const date = new Date(context[0].parsed.x);
                            return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
                        },
                        label: function(context) {
                            const point = context.raw;
                            const aqi = point.y;
                            const category = getAQICategory(aqi);
                            
                            return [
                                `AQI: ${aqi} (${category})`,
                                `PM2.5: ${point.pm25 || 'N/A'} μg/m³`,
                                `PM10: ${point.pm10 || 'N/A'} μg/m³`,
                                `NO2: ${point.no2 || 'N/A'} μg/m³`,
                                `SO2: ${point.so2 || 'N/A'} μg/m³`,
                                `CO: ${point.co || 'N/A'} mg/m³`,
                                `O3: ${point.o3 || 'N/A'} μg/m³`
                            ];
                        },
                        labelColor: function(context) {
                            const aqi = context.raw.y;
                            return {
                                borderColor: getAQIColor(aqi),
                                backgroundColor: getAQIColor(aqi)
                            };
                        }
                    }
                }
            },
            elements: {
                point: {
                    hoverBorderWidth: 3
                }
            },
            animation: {
                duration: 1000,
                easing: 'easeOutQuart'
            }
        }
    });
    
    // Show chart container with animation
    const chartContainer = document.getElementById('chartContainer');
    chartContainer.style.display = 'block';
    chartContainer.style.opacity = '0';
    setTimeout(() => {
        chartContainer.style.transition = 'opacity 0.5s ease';
        chartContainer.style.opacity = '1';
    }, 100);
    
    return currentChart;
}

// Create comparison chart for multiple cities
function createComparisonChart(citiesData) {
    const ctx = document.getElementById('aqiChart').getContext('2d');
    
    // Destroy existing chart if it exists
    if (currentChart) {
        currentChart.destroy();
        currentChart = null;
    }
    
    const datasets = [];
    const colors = ['#54a0ff', '#5f27cd', '#00d2d3', '#ff9ff3', '#54a0ff', '#10ac84'];
    
    Object.keys(citiesData).forEach((city, index) => {
        const data = citiesData[city];
        const chartData = data.map(item => ({
            x: new Date(item.timestamp),
            y: item.aqiValue
        }));
        
        datasets.push({
            label: city,
            data: chartData,
            borderColor: colors[index % colors.length],
            backgroundColor: colors[index % colors.length] + '20',
            borderWidth: 2,
            fill: false,
            tension: 0.4,
            pointRadius: 3,
            pointHoverRadius: 6
        });
    });
    
    currentChart = new Chart(ctx, {
        type: 'line',
        data: { datasets },
        options: {
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
                            hour: 'MMM dd HH:mm',
                            day: 'MMM dd'
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
                        color: 'rgba(255, 255, 255, 0.7)'
                    }
                }
            },
            plugins: {
                title: {
                    display: true,
                    text: 'Multi-City AQI Comparison',
                    color: 'rgba(255, 255, 255, 0.9)',
                    font: { size: 16 }
                },
                legend: {
                    labels: {
                        color: 'rgba(255, 255, 255, 0.9)'
                    }
                },
                tooltip: {
                    backgroundColor: 'rgba(0, 0, 0, 0.8)',
                    titleColor: 'white',
                    bodyColor: 'white'
                }
            }
        }
    });
    
    return currentChart;
}

// Calculate and display statistics from chart data
function calculateChartStatistics(data) {
    if (!data || data.length === 0) {
        return {
            average: 0,
            maximum: 0,
            minimum: 0,
            count: 0
        };
    }
    
    const aqiValues = data.map(item => item.aqiValue).filter(val => val !== null && val !== undefined);
    
    if (aqiValues.length === 0) {
        return {
            average: 0,
            maximum: 0,
            minimum: 0,
            count: 0
        };
    }
    
    const sum = aqiValues.reduce((acc, val) => acc + val, 0);
    const average = Math.round(sum / aqiValues.length);
    const maximum = Math.max(...aqiValues);
    const minimum = Math.min(...aqiValues);
    
    return {
        average,
        maximum,
        minimum,
        count: data.length
    };
}

// Update statistics display
function updateStatisticsDisplay(stats) {
    document.getElementById('avgAqi').textContent = stats.average;
    document.getElementById('maxAqi').textContent = stats.maximum;
    document.getElementById('minAqi').textContent = stats.minimum;
    document.getElementById('dataPoints').textContent = stats.count;
    
    // Show stats container with animation
    const statsContainer = document.getElementById('chartStats');
    statsContainer.style.display = 'block';
    
    // Add color coding to statistics based on AQI values
    const avgElement = document.getElementById('avgAqi');
    const maxElement = document.getElementById('maxAqi');
    const minElement = document.getElementById('minAqi');
    
    avgElement.style.color = getAQIColor(stats.average);
    maxElement.style.color = getAQIColor(stats.maximum);
    minElement.style.color = getAQIColor(stats.minimum);
}

// Destroy current chart
function destroyChart() {
    if (currentChart) {
        currentChart.destroy();
        currentChart = null;
    }
    
    // Hide containers
    document.getElementById('chartContainer').style.display = 'none';
    document.getElementById('chartStats').style.display = 'none';
}

// Export chart as image
function exportChartAsImage(filename = 'aqi-chart.png') {
    if (!currentChart) {
        console.warn('No chart available to export');
        return;
    }
    
    const url = currentChart.toBase64Image();
    const link = document.createElement('a');
    link.download = filename;
    link.href = url;
    link.click();
}

// Resize chart when container changes
function resizeChart() {
    if (currentChart) {
        currentChart.resize();
    }
}

// Add window resize listener
window.addEventListener('resize', resizeChart);

// Export functions for use in other files
window.ChartManager = {
    create: createHistoricalChart,
    createComparison: createComparisonChart,
    calculateStats: calculateChartStatistics,
    updateStats: updateStatisticsDisplay,
    destroy: destroyChart,
    export: exportChartAsImage,
    resize: resizeChart,
    getAQIColor: getAQIColor,
    getAQICategory: getAQICategory
};
