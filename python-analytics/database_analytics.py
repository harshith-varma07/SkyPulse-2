import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from matplotlib.dates import DateFormatter
from matplotlib.backends.backend_agg import FigureCanvasAgg
from reportlab.lib.pagesizes import letter, A4
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Image, Table, TableStyle, PageBreak
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import inch
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT, TA_RIGHT
from datetime import datetime, timedelta
import io
import base64
import json
import sys
import os
import warnings
import pymysql
from sqlalchemy import create_engine, text
import logging

warnings.filterwarnings('ignore')

# Set matplotlib to use non-interactive backend
plt.switch_backend('Agg')

# Configure matplotlib for better looking charts
plt.style.use('dark_background')
sns.set_palette("husl")

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class DatabaseAirQualityAnalytics:
    def __init__(self, db_host='localhost', db_port=3306, db_name='air_quality_monitoring', 
                 db_user='root', db_password='Mformysql@12'):
        """Initialize the analytics service with database connection"""
        self.db_config = {
            'host': db_host,
            'port': db_port,
            'database': db_name,
            'user': db_user,
            'password': db_password
        }
        
        self.aqi_categories = {
            'Good': (0, 50, '#00ff88'),
            'Moderate': (51, 100, '#ffff00'),
            'Unhealthy for Sensitive Groups': (101, 150, '#ff8800'),
            'Unhealthy': (151, 200, '#ff0000'),
            'Very Unhealthy': (201, 300, '#8800ff'),
            'Hazardous': (301, 500, '#880000')
        }
        
        # Initialize database connection
        self.engine = self._create_database_connection()
        
    def _create_database_connection(self):
        """Create SQLAlchemy database engine"""
        try:
            # Create connection string for MySQL
            connection_string = (
                f"mysql+pymysql://{self.db_config['user']}:{self.db_config['password']}"
                f"@{self.db_config['host']}:{self.db_config['port']}/{self.db_config['database']}"
                f"?charset=utf8mb4"
            )
            
            engine = create_engine(connection_string, echo=False, pool_pre_ping=True)
            
            # Test connection
            with engine.connect() as conn:
                conn.execute(text("SELECT 1"))
                logger.info("Database connection established successfully")
            
            return engine
        except Exception as e:
            logger.error(f"Failed to create database connection: {str(e)}")
            raise Exception(f"Database connection failed: {str(e)}")
    
    def check_city_and_data_availability(self, city_name, start_date, end_date):
        """
        Check data availability for city and time period.
        Returns: (scenario, data_info)
        Scenarios:
        1: City and time period data available
        2: City available but time period data missing/partial
        3: City not available in database
        """
        try:
            # Check if city exists in database
            city_check_query = """
                SELECT COUNT(*) as city_count,
                       MIN(timestamp) as earliest_data,
                       MAX(timestamp) as latest_data
                FROM aqi_data 
                WHERE city = %s
            """
            
            with self.engine.connect() as conn:
                result = conn.execute(text(city_check_query), (city_name,)).fetchone()
                
                if result.city_count == 0:
                    return 3, {"message": f"City '{city_name}' not found in database"}
                
                earliest_data = result.earliest_data
                latest_data = result.latest_data
                
                # Check if requested time period has data
                period_check_query = """
                    SELECT COUNT(*) as period_count
                    FROM aqi_data 
                    WHERE city = %s 
                    AND timestamp BETWEEN %s AND %s
                """
                
                period_result = conn.execute(text(period_check_query), 
                                           (city_name, start_date, end_date)).fetchone()
                
                if period_result.period_count > 0:
                    return 1, {
                        "message": f"Data available for {city_name} in requested period",
                        "records_in_period": period_result.period_count,
                        "earliest_data": earliest_data,
                        "latest_data": latest_data
                    }
                else:
                    return 2, {
                        "message": f"City '{city_name}' exists but no data for requested period",
                        "earliest_data": earliest_data,
                        "latest_data": latest_data,
                        "total_records": result.city_count
                    }
                    
        except Exception as e:
            logger.error(f"Error checking data availability: {str(e)}")
            raise Exception(f"Error checking data availability: {str(e)}")
    
    def fetch_data_from_database(self, city_name, start_date=None, end_date=None):
        """
        Fetch air quality data from MySQL database
        Handles the 3 scenarios based on data availability
        """
        try:
            # Parse dates if they are strings
            if isinstance(start_date, str):
                start_date = pd.to_datetime(start_date)
            if isinstance(end_date, str):
                end_date = pd.to_datetime(end_date)
            
            # If no dates provided, use last 30 days
            if not end_date:
                end_date = datetime.now()
            if not start_date:
                start_date = end_date - timedelta(days=30)
            
            # Check data availability scenario
            scenario, data_info = self.check_city_and_data_availability(city_name, start_date, end_date)
            
            if scenario == 3:
                # City not found
                raise Exception(f"City '{city_name}' not found in database. {data_info['message']}")
            
            # For scenarios 1 and 2, fetch available data
            if scenario == 1:
                # Data available for requested period
                query = """
                    SELECT city, aqi_value, pm25, pm10, no2, so2, co, o3, timestamp
                    FROM aqi_data 
                    WHERE city = %s 
                    AND timestamp BETWEEN %s AND %s
                    ORDER BY timestamp
                """
                params = (city_name, start_date, end_date)
            else:
                # Scenario 2: Use all available data for the city
                query = """
                    SELECT city, aqi_value, pm25, pm10, no2, so2, co, o3, timestamp
                    FROM aqi_data 
                    WHERE city = %s
                    ORDER BY timestamp
                """
                params = (city_name,)
            
            # Execute query and fetch data
            with self.engine.connect() as conn:
                df = pd.read_sql(text(query), conn, params=params)
            
            if df.empty:
                raise Exception(f"No data found for city '{city_name}'")
            
            # Process the data
            df = self.process_database_data(df)
            
            return df, scenario, data_info
            
        except Exception as e:
            logger.error(f"Error fetching data from database: {str(e)}")
            raise Exception(f"Database query failed: {str(e)}")
    
    def process_database_data(self, df):
        """Process data fetched from database"""
        try:
            # Ensure timestamp is datetime
            df['timestamp'] = pd.to_datetime(df['timestamp'])
            df = df.sort_values('timestamp')
            
            # Add derived columns
            df['aqi_category'] = df['aqi_value'].apply(lambda x: self.get_aqi_category(x)[0])
            df['aqi_color'] = df['aqi_value'].apply(lambda x: self.get_aqi_category(x)[1])
            
            # Handle missing pollutant values
            pollutant_columns = ['pm25', 'pm10', 'no2', 'so2', 'co', 'o3']
            for col in pollutant_columns:
                if col in df.columns:
                    df[col] = pd.to_numeric(df[col], errors='coerce')
                else:
                    df[col] = None
            
            return df
        except Exception as e:
            raise Exception(f"Error processing database data: {str(e)}")
    
    def get_aqi_category(self, aqi_value):
        """Get AQI category name and color based on value"""
        for category, (min_val, max_val, color) in self.aqi_categories.items():
            if min_val <= aqi_value <= max_val:
                return category, color
        return 'Hazardous', '#880000'
    
    def calculate_statistics(self, df, scenario, data_info):
        """Calculate comprehensive statistics from the data"""
        stats = {}
        
        try:
            # Basic statistics
            stats['scenario'] = scenario
            stats['data_info'] = data_info
            stats['total_records'] = len(df)
            stats['avg_aqi'] = round(df['aqi_value'].mean(), 2)
            stats['max_aqi'] = int(df['aqi_value'].max())
            stats['min_aqi'] = int(df['aqi_value'].min())
            stats['std_aqi'] = round(df['aqi_value'].std(), 2)
            
            # Time period
            stats['start_date'] = df['timestamp'].min().strftime('%Y-%m-%d %H:%M')
            stats['end_date'] = df['timestamp'].max().strftime('%Y-%m-%d %H:%M')
            stats['duration_hours'] = round((df['timestamp'].max() - df['timestamp'].min()).total_seconds() / 3600, 2)
            
            # Category distribution
            category_counts = df['aqi_category'].value_counts()
            stats['category_distribution'] = category_counts.to_dict()
            
            # Pollutant averages
            pollutants = ['pm25', 'pm10', 'no2', 'so2', 'co', 'o3']
            stats['pollutant_averages'] = {}
            for pollutant in pollutants:
                if pollutant in df.columns and not df[pollutant].isna().all():
                    stats['pollutant_averages'][pollutant] = round(df[pollutant].mean(), 2)
                else:
                    stats['pollutant_averages'][pollutant] = None
            
            # Peak pollution times
            peak_hour = df.loc[df['aqi_value'].idxmax(), 'timestamp'].hour
            stats['peak_pollution_hour'] = peak_hour
            
            # Trend analysis (simple)
            if len(df) > 1:
                trend_coef = np.polyfit(range(len(df)), df['aqi_value'], 1)[0]
                stats['trend'] = 'Increasing' if trend_coef > 0.5 else 'Decreasing' if trend_coef < -0.5 else 'Stable'
            else:
                stats['trend'] = 'Insufficient data'
            
            return stats
        except Exception as e:
            raise Exception(f"Error calculating statistics: {str(e)}")
    
    def create_line_chart(self, df, city_name):
        """Create AQI trend line chart as requested"""
        try:
            fig, ax = plt.subplots(figsize=(12, 6), facecolor='#0f0f23', edgecolor='none')
            ax.set_facecolor('#1a1a2e')
            
            # Create the line plot
            ax.plot(df['timestamp'], df['aqi_value'], 
                   color='#54a0ff', linewidth=3, alpha=0.8, label='AQI Value')
            
            # Add scatter points with AQI colors
            for _, row in df.iterrows():
                ax.scatter(row['timestamp'], row['aqi_value'], 
                          color=row['aqi_color'], s=50, alpha=0.8, zorder=5)
            
            # Add AQI category background zones
            ax.axhspan(0, 50, alpha=0.1, color='#00ff88', label='Good')
            ax.axhspan(51, 100, alpha=0.1, color='#ffff00', label='Moderate')
            ax.axhspan(101, 150, alpha=0.1, color='#ff8800', label='Unhealthy for Sensitive')
            ax.axhspan(151, 200, alpha=0.1, color='#ff0000', label='Unhealthy')
            ax.axhspan(201, 300, alpha=0.1, color='#8800ff', label='Very Unhealthy')
            ax.axhspan(301, 500, alpha=0.1, color='#880000', label='Hazardous')
            
            # Formatting
            ax.set_xlabel('Time', color='white', fontsize=12)
            ax.set_ylabel('AQI Value', color='white', fontsize=12)
            ax.set_title(f'AQI Line Chart - {city_name}', color='white', fontsize=16, fontweight='bold')
            ax.grid(True, alpha=0.3, color='white')
            ax.tick_params(colors='white')
            
            # Format x-axis
            if len(df) > 0:
                date_format = DateFormatter('%m/%d %H:%M')
                ax.xaxis.set_major_formatter(date_format)
                plt.xticks(rotation=45)
            
            plt.tight_layout()
            return self.fig_to_base64(fig)
        except Exception as e:
            raise Exception(f"Error creating line chart: {str(e)}")
    
    def create_histogram(self, df, city_name):
        """Create AQI distribution histogram as requested"""
        try:
            fig, ax = plt.subplots(figsize=(10, 6), facecolor='#0f0f23', edgecolor='none')
            ax.set_facecolor('#1a1a2e')
            
            # Create histogram
            bins = [0, 50, 100, 150, 200, 300, 500]  # AQI category boundaries
            colors = ['#00ff88', '#ffff00', '#ff8800', '#ff0000', '#8800ff', '#880000']
            
            n, bins, patches = ax.hist(df['aqi_value'], bins=bins, alpha=0.8, edgecolor='white', linewidth=1)
            
            # Color bars according to AQI categories
            for i, patch in enumerate(patches):
                patch.set_facecolor(colors[i])
            
            # Add value labels on bars
            for i, count in enumerate(n):
                if count > 0:
                    ax.text(bins[i] + (bins[i+1] - bins[i])/2, count + max(n)*0.01,
                           str(int(count)), ha='center', va='bottom', color='white', fontweight='bold')
            
            ax.set_xlabel('AQI Value', color='white', fontsize=12)
            ax.set_ylabel('Frequency', color='white', fontsize=12)
            ax.set_title(f'AQI Distribution Histogram - {city_name}', color='white', fontsize=16, fontweight='bold')
            ax.grid(True, alpha=0.3, axis='y', color='white')
            ax.tick_params(colors='white')
            
            # Add category labels
            category_labels = ['Good\n(0-50)', 'Moderate\n(51-100)', 'Unhealthy for\nSensitive (101-150)',
                             'Unhealthy\n(151-200)', 'Very Unhealthy\n(201-300)', 'Hazardous\n(301-500)']
            for i, label in enumerate(category_labels):
                ax.text(bins[i] + (bins[i+1] - bins[i])/2, -max(n)*0.05, label, 
                       ha='center', va='top', color='white', fontsize=8, rotation=0)
            
            plt.tight_layout()
            return self.fig_to_base64(fig)
        except Exception as e:
            raise Exception(f"Error creating histogram: {str(e)}")
    
    def fig_to_base64(self, fig):
        """Convert matplotlib figure to base64 string"""
        try:
            buffer = io.BytesIO()
            fig.savefig(buffer, format='png', dpi=150, bbox_inches='tight', 
                       facecolor='#0f0f23', edgecolor='none')
            buffer.seek(0)
            image_base64 = base64.b64encode(buffer.getvalue()).decode('utf-8')
            buffer.close()
            plt.close(fig)
            return image_base64
        except Exception as e:
            raise Exception(f"Error converting figure to base64: {str(e)}")
    
    def base64_to_reportlab_image(self, base64_string, width=6*inch, height=4*inch):
        """Convert base64 string to ReportLab Image object"""
        try:
            image_data = base64.b64decode(base64_string)
            image_buffer = io.BytesIO(image_data)
            return Image(image_buffer, width=width, height=height)
        except Exception as e:
            raise Exception(f"Error converting base64 to ReportLab image: {str(e)}")
    
    def generate_pdf_report(self, city_name, start_date, end_date):
        """
        Generate comprehensive PDF report with scenarios handling
        Page 1: Air quality details and recommendations
        Page 2: Line chart and histogram
        """
        try:
            # Fetch data from database
            df, scenario, data_info = self.fetch_data_from_database(city_name, start_date, end_date)
            stats = self.calculate_statistics(df, scenario, data_info)
            
            # Create buffer for PDF
            buffer = io.BytesIO()
            
            # Create PDF document
            doc = SimpleDocTemplate(buffer, pagesize=A4, topMargin=1*inch, bottomMargin=1*inch)
            styles = getSampleStyleSheet()
            
            # Custom styles
            title_style = ParagraphStyle(
                'CustomTitle',
                parent=styles['Title'],
                fontSize=20,
                textColor=colors.HexColor('#2c3e50'),
                alignment=TA_CENTER,
                spaceAfter=30
            )
            
            heading_style = ParagraphStyle(
                'CustomHeading',
                parent=styles['Heading2'],
                fontSize=14,
                textColor=colors.HexColor('#34495e'),
                spaceBefore=20,
                spaceAfter=10
            )
            
            # Build PDF content
            content = []
            
            # Title
            title = Paragraph(f"Air Quality Report - {city_name}", title_style)
            content.append(title)
            
            # Scenario information
            scenario_info = f"""
            <b>Data Analysis Scenario:</b> {scenario}<br/>
            <b>Status:</b> {data_info['message']}<br/>
            <b>Report Generated:</b> {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}<br/>
            <b>Analysis Period:</b> {stats['start_date']} to {stats['end_date']}<br/>
            <b>Duration:</b> {stats['duration_hours']} hours<br/>
            <b>Data Points:</b> {stats['total_records']} readings
            """
            content.append(Paragraph(scenario_info, styles['Normal']))
            content.append(Spacer(1, 20))
            
            # Air Quality Details
            content.append(Paragraph("Air Quality Analysis", heading_style))
            
            analysis_text = f"""
            The air quality analysis for {city_name} reveals an average AQI of {stats['avg_aqi']}, 
            ranging from {stats['min_aqi']} to {stats['max_aqi']}. The overall trend shows {stats['trend'].lower()} 
            air quality levels during the analysis period. Peak pollution typically occurs around 
            {stats['peak_pollution_hour']}:00 hours.
            """
            content.append(Paragraph(analysis_text, styles['Normal']))
            content.append(Spacer(1, 20))
            
            # Key Statistics Table
            content.append(Paragraph("Key Statistics", heading_style))
            
            stats_data = [
                ['Metric', 'Value', 'Category'],
                ['Average AQI', str(stats['avg_aqi']), self.get_aqi_category(stats['avg_aqi'])[0]],
                ['Maximum AQI', str(stats['max_aqi']), self.get_aqi_category(stats['max_aqi'])[0]],
                ['Minimum AQI', str(stats['min_aqi']), self.get_aqi_category(stats['min_aqi'])[0]],
                ['Standard Deviation', str(stats['std_aqi']), '-'],
                ['Peak Pollution Hour', f"{stats['peak_pollution_hour']}:00", '-'],
                ['Overall Trend', stats['trend'], '-']
            ]
            
            stats_table = Table(stats_data, colWidths=[2*inch, 1.5*inch, 2.5*inch])
            stats_table.setStyle(TableStyle([
                ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#34495e')),
                ('TEXTCOLOR', (0, 0), (-1, 0), colors.whitesmoke),
                ('ALIGN', (0, 0), (-1, -1), 'CENTER'),
                ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
                ('FONTSIZE', (0, 0), (-1, 0), 12),
                ('BOTTOMPADDING', (0, 0), (-1, 0), 12),
                ('BACKGROUND', (0, 1), (-1, -1), colors.beige),
                ('GRID', (0, 0), (-1, -1), 1, colors.black)
            ]))
            content.append(stats_table)
            content.append(Spacer(1, 20))
            
            # Health Recommendations
            content.append(Paragraph("Health Recommendations & Precautions", heading_style))
            
            avg_aqi = stats['avg_aqi']
            if avg_aqi <= 50:
                health_text = """
                <b>Good Air Quality (0-50 AQI)</b><br/>
                • Air quality is considered satisfactory and poses little or no risk<br/>
                • Perfect conditions for all outdoor activities<br/>
                • No health precautions necessary<br/>
                • Ideal time for exercise, sports, and outdoor recreation<br/>
                • Windows can be kept open for natural ventilation
                """
            elif avg_aqi <= 100:
                health_text = """
                <b>Moderate Air Quality (51-100 AQI)</b><br/>
                • Air quality is acceptable for most people<br/>
                • Unusually sensitive individuals may experience minor symptoms<br/>
                • Consider reducing prolonged outdoor exertion if you're unusually sensitive<br/>
                • Monitor air quality if you have respiratory conditions<br/>
                • Generally safe for outdoor activities
                """
            elif avg_aqi <= 150:
                health_text = """
                <b>Unhealthy for Sensitive Groups (101-150 AQI)</b><br/>
                • Sensitive groups may experience health effects<br/>
                • People with heart/lung disease, children, and older adults should limit prolonged outdoor activities<br/>
                • Consider wearing a mask when outdoors for extended periods<br/>
                • Keep windows closed and use air conditioning if available<br/>
                • Monitor symptoms if you're in a sensitive group
                """
            else:
                health_text = """
                <b>Unhealthy Air Quality (151+ AQI)</b><br/>
                • Everyone may begin to experience health effects<br/>
                • Sensitive groups may experience more serious effects<br/>
                • Avoid prolonged outdoor activities and heavy exertion<br/>
                • Stay indoors and use air purifiers when possible<br/>
                • Wear N95 or equivalent masks when going outside<br/>
                • Seek medical attention if experiencing respiratory symptoms
                """
            
            content.append(Paragraph(health_text, styles['Normal']))
            
            # Page break before charts (Page 2)
            content.append(PageBreak())
            
            # Charts Section (Page 2)
            content.append(Paragraph("Data Visualization", heading_style))
            
            try:
                # Line Chart
                content.append(Paragraph("AQI Trend Line Chart", styles['Heading3']))
                line_chart_b64 = self.create_line_chart(df, city_name)
                line_chart_img = self.base64_to_reportlab_image(line_chart_b64, width=7*inch, height=4*inch)
                content.append(line_chart_img)
                content.append(Spacer(1, 30))
                
                # Histogram
                content.append(Paragraph("AQI Distribution Histogram", styles['Heading3']))
                histogram_b64 = self.create_histogram(df, city_name)
                histogram_img = self.base64_to_reportlab_image(histogram_b64, width=7*inch, height=4*inch)
                content.append(histogram_img)
                
            except Exception as chart_error:
                content.append(Paragraph(f"Note: Some charts could not be generated: {str(chart_error)}", styles['Normal']))
            
            # Footer
            content.append(Spacer(1, 30))
            footer_text = """
            <i>This report was generated by the AirSight Python Analytics System with direct database connectivity. 
            Data is retrieved from the real-time air quality monitoring database.</i>
            """
            content.append(Paragraph(footer_text, styles['Italic']))
            
            # Build PDF
            doc.build(content)
            
            # Get PDF data
            pdf_data = buffer.getvalue()
            buffer.close()
            
            return pdf_data
            
        except Exception as e:
            raise Exception(f"Error generating PDF report: {str(e)}")

def main():
    """Main function to handle command line usage"""
    try:
        if len(sys.argv) < 4:
            print("Usage: python database_analytics.py <city_name> <operation> <start_date> [end_date] [db_host] [db_user] [db_password]")
            print("Operations: stats, pdf, line_chart, histogram")
            print("Dates format: YYYY-MM-DD or YYYY-MM-DD HH:MM:SS")
            sys.exit(1)
        
        city_name = sys.argv[1]
        operation = sys.argv[2]
        start_date = sys.argv[3] if sys.argv[3] != 'null' else None
        end_date = sys.argv[4] if len(sys.argv) > 4 and sys.argv[4] != 'null' else None
        
        # Optional database configuration
        db_host = sys.argv[5] if len(sys.argv) > 5 else 'localhost'
        db_user = sys.argv[6] if len(sys.argv) > 6 else 'root'
        db_password = sys.argv[7] if len(sys.argv) > 7 else ''
        
        analytics = DatabaseAirQualityAnalytics(
            db_host=db_host, 
            db_user=db_user, 
            db_password=db_password
        )
        
        if operation == "stats":
            df, scenario, data_info = analytics.fetch_data_from_database(city_name, start_date, end_date)
            stats = analytics.calculate_statistics(df, scenario, data_info)
            print(json.dumps(stats, indent=2, default=str))
            
        elif operation == "pdf":
            pdf_data = analytics.generate_pdf_report(city_name, start_date, end_date)
            # Write PDF data to stdout as binary
            sys.stdout.buffer.write(pdf_data)
            return
            
        elif operation == "line_chart":
            df, scenario, data_info = analytics.fetch_data_from_database(city_name, start_date, end_date)
            chart_b64 = analytics.create_line_chart(df, city_name)
            print(chart_b64)
            
        elif operation == "histogram":
            df, scenario, data_info = analytics.fetch_data_from_database(city_name, start_date, end_date)
            chart_b64 = analytics.create_histogram(df, city_name)
            print(chart_b64)
            
        else:
            print(f"Unknown operation: {operation}")
            sys.exit(1)
            
    except Exception as e:
        print(f"Error: {str(e)}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()