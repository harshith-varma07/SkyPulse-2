# Python Analytics Implementation

This document describes the new Python-based analytics system that replaced the Java analytics implementation.

## Overview

The analytics system has been completely migrated from Java to Python with direct MySQL database connectivity. The system now handles data visualization and PDF generation using Python libraries.

## Architecture

### Python Analytics Service (`database_analytics.py`)
- **Direct Database Connectivity**: Uses PyMySQL and SQLAlchemy for MySQL connections
- **Scenario-Based Data Handling**: Handles 3 scenarios as requested:
  1. City and time period data available
  2. City available but time period data missing/partial  
  3. City not available in database
- **Visualization**: Creates line charts and histograms using matplotlib
- **PDF Generation**: Uses ReportLab for comprehensive PDF reports

### Java Integration (`PythonAnalyticsController.java`)
- **REST API Endpoints**: Provides clean REST endpoints that call Python service
- **Process Management**: Handles Python process execution and data transfer
- **Error Handling**: Proper error handling for all scenarios

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/analytics/pdf` | GET | Generate comprehensive PDF report |
| `/api/analytics/charts/line` | GET | Generate AQI trend line chart |
| `/api/analytics/charts/histogram` | GET | Generate AQI distribution histogram |
| `/api/analytics/stats` | GET | Get statistical analysis |

### Parameters
- `city` (required): City name
- `startDate` (optional): Start date in ISO format
- `endDate` (optional): End date in ISO format
- Authentication: Requires `X-User-Id` header

## Data Scenarios

### Scenario 1: Complete Data Available
When city exists and data is available for the requested time period:
- Returns full analysis and visualizations for the specified period
- Includes comprehensive statistics and trend analysis

### Scenario 2: Partial Data Available  
When city exists but requested time period has no data:
- Uses all available data for that city
- Provides analysis based on the available date range
- Clearly indicates the actual data period used

### Scenario 3: City Not Found
When city is not available in the database:
- Returns appropriate error message
- HTTP 404 status code
- Clear error description

## Charts and Visualizations

### Line Chart
- Shows AQI trend over time
- Color-coded points based on AQI categories
- Background zones indicating AQI quality levels
- Interactive time axis formatting

### Histogram
- AQI value distribution across categories
- Color-coded bars matching AQI categories
- Frequency analysis of pollution levels
- Category labels and statistics

## PDF Report Structure

### Page 1: Analysis and Recommendations
- Executive summary with scenario information
- Key statistics table
- Air quality analysis
- Health recommendations based on AQI levels
- Precautionary measures

### Page 2: Data Visualization
- AQI trend line chart
- AQI distribution histogram
- Professional formatting and styling

## Database Configuration

The Python analytics service connects directly to MySQL:
- Default: `localhost:3306`
- Database: `air_quality_monitoring`
- Configurable host, user, and password parameters
- Connection pooling and error handling

## Dependencies

### Python Libraries
- pandas: Data manipulation and analysis
- numpy: Numerical computations
- matplotlib: Chart generation
- seaborn: Statistical visualizations
- reportlab: PDF generation
- pymysql: MySQL connectivity
- sqlalchemy: Database ORM

### Removed Java Dependencies
- iText PDF library
- All Java analytics services
- Custom analytics utilities

## Usage Examples

### Command Line Usage
```bash
# Get statistics
python3 database_analytics.py "London" stats "2024-01-01" "2024-01-31"

# Generate PDF report
python3 database_analytics.py "London" pdf "2024-01-01" "2024-01-31" > report.pdf

# Generate line chart
python3 database_analytics.py "London" line_chart "2024-01-01" "2024-01-31"

# Generate histogram  
python3 database_analytics.py "London" histogram "2024-01-01" "2024-01-31"
```

### REST API Usage
```bash
# Get PDF report
curl -H "X-User-Id: user123" \
  "http://localhost:8080/api/analytics/pdf?city=London&startDate=2024-01-01T00:00:00&endDate=2024-01-31T23:59:59" \
  --output report.pdf

# Get line chart (returns base64 image)
curl -H "X-User-Id: user123" \
  "http://localhost:8080/api/analytics/charts/line?city=London&startDate=2024-01-01T00:00:00&endDate=2024-01-31T23:59:59"

# Get statistics (returns JSON)
curl -H "X-User-Id: user123" \
  "http://localhost:8080/api/analytics/stats?city=London&startDate=2024-01-01T00:00:00&endDate=2024-01-31T23:59:59"
```

## Error Handling

The system provides appropriate error responses for:
- City not found in database
- Database connection issues
- Invalid date ranges
- Python process execution errors
- Authentication failures

## Performance Considerations

- Direct database queries optimized for analytics workloads
- Efficient data processing with pandas
- Process timeout handling (60-120 seconds)
- Memory management for large datasets
- Connection pooling for database efficiency

## Migration Notes

### What Was Removed
- 8 Java analytics files (services, controllers, utilities)
- iText PDF dependency
- Custom analytics caching mechanisms
- Java-based chart generation

### What Was Added
- Comprehensive Python analytics service
- Direct MySQL database connectivity
- Professional PDF report generation
- Modern data visualization with matplotlib
- Scenario-based data handling
- Clean REST API integration

The new system provides more flexibility, better visualization capabilities, and cleaner separation between the Java application layer and analytics processing.