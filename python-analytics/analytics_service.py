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
warnings.filterwarnings('ignore')

# Set matplotlib to use non-interactive backend
plt.switch_backend('Agg')

# Configure matplotlib for better looking charts
plt.style.use('dark_background')
sns.set_palette("husl")

class AirQualityAnalytics:
    def __init__(self):
        """Initialize the analytics service"""
        self.aqi_categories = {
            'Good': (0, 50, '#00ff88'),
            'Moderate': (51, 100, '#ffff00'),
            'Unhealthy for Sensitive Groups': (101, 150, '#ff8800'),
            'Unhealthy': (151, 200, '#ff0000'),
            'Very Unhealthy': (201, 300, '#8800ff'),
            'Hazardous': (301, 500, '#880000')
        }
        
    def get_aqi_category(self, aqi_value):
        """Get AQI category name and color based on value"""
        for category, (min_val, max_val, color) in self.aqi_categories.items():
            if min_val <= aqi_value <= max_val:
                return category, color
        return 'Hazardous', '#880000'
    
    def process_data(self, data_input):
        """Process raw data into pandas DataFrame - legacy method for compatibility"""
        return self.process_real_time_data(data_input)
    
    def calculate_statistics(self, df):
        """Calculate comprehensive statistics from the data"""
        stats = {}
        
        try:
            # Basic statistics
            stats['total_records'] = len(df)
            stats['avg_aqi'] = round(df['aqiValue'].mean(), 2)
            stats['max_aqi'] = int(df['aqiValue'].max())
            stats['min_aqi'] = int(df['aqiValue'].min())
            stats['std_aqi'] = round(df['aqiValue'].std(), 2)
            
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
            peak_hour = df.loc[df['aqiValue'].idxmax(), 'timestamp'].hour
            stats['peak_pollution_hour'] = peak_hour
            
            # Trend analysis (simple)
            if len(df) > 1:
                trend_coef = np.polyfit(range(len(df)), df['aqiValue'], 1)[0]
                stats['trend'] = 'Increasing' if trend_coef > 0.5 else 'Decreasing' if trend_coef < -0.5 else 'Stable'
            else:
                stats['trend'] = 'Insufficient data'
            
            return stats
        except Exception as e:
            raise Exception(f"Error calculating statistics: {str(e)}")
    
    def create_aqi_trend_chart(self, df, city_name):
        """Create AQI trend over time chart"""
        try:
            fig, ax = plt.subplots(figsize=(12, 6), facecolor='#0f0f23', edgecolor='none')
            ax.set_facecolor('#1a1a2e')
            
            # Create the line plot
            ax.plot(df['timestamp'], df['aqiValue'], 
                   color='#54a0ff', linewidth=3, alpha=0.8, label='AQI Value')
            
            # Add scatter points with AQI colors
            for _, row in df.iterrows():
                ax.scatter(row['timestamp'], row['aqiValue'], 
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
            ax.set_title(f'AQI Trend Over Time - {city_name}', color='white', fontsize=16, fontweight='bold')
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
            raise Exception(f"Error creating AQI trend chart: {str(e)}")
    
    def create_pollutants_bar_chart(self, df, city_name):
        """Create average pollutants bar chart"""
        try:
            fig, ax = plt.subplots(figsize=(10, 6), facecolor='#0f0f23', edgecolor='none')
            ax.set_facecolor('#1a1a2e')
            
            pollutants = {
                'PM2.5': 'pm25',
                'PM10': 'pm10',
                'NO2': 'no2',
                'SO2': 'so2',
                'CO': 'co',
                'O3': 'o3'
            }
            
            values = []
            labels = []
            colors = ['#54a0ff', '#5f27cd', '#ff9ff3', '#ff6b6b', '#10ac84', '#feca57']
            
            for i, (label, col) in enumerate(pollutants.items()):
                if col in df.columns and not df[col].isna().all():
                    avg_value = df[col].mean()
                    values.append(avg_value)
                    labels.append(label)
                else:
                    values.append(0)
                    labels.append(label)
            
            bars = ax.bar(labels, values, color=colors[:len(labels)], alpha=0.8, edgecolor='white', linewidth=1)
            
            # Add value labels on bars
            for bar, value in zip(bars, values):
                if value > 0:
                    ax.text(bar.get_x() + bar.get_width()/2, bar.get_height() + max(values)*0.01,
                           f'{value:.1f}', ha='center', va='bottom', color='white', fontweight='bold')
            
            ax.set_xlabel('Pollutants', color='white', fontsize=12)
            ax.set_ylabel('Average Concentration (µg/m³)', color='white', fontsize=12)
            ax.set_title(f'Average Pollutant Levels - {city_name}', color='white', fontsize=16, fontweight='bold')
            ax.grid(True, alpha=0.3, axis='y', color='white')
            ax.tick_params(colors='white')
            
            plt.tight_layout()
            return self.fig_to_base64(fig)
        except Exception as e:
            raise Exception(f"Error creating pollutants bar chart: {str(e)}")
    
    def create_aqi_categories_pie_chart(self, df, city_name):
        """Create AQI categories distribution pie chart"""
        try:
            fig, ax = plt.subplots(figsize=(10, 8), facecolor='#0f0f23', edgecolor='none')
            
            category_counts = df['aqi_category'].value_counts()
            
            # Get colors for each category
            colors_list = []
            for category in category_counts.index:
                colors_list.append(self.aqi_categories.get(category, '#888888')[2])
            
            wedges, texts, autotexts = ax.pie(category_counts.values, 
                                            labels=category_counts.index, 
                                            colors=colors_list,
                                            autopct='%1.1f%%',
                                            startangle=90,
                                            textprops={'color': 'white', 'fontsize': 10})
            
            # Enhance text appearance
            for autotext in autotexts:
                autotext.set_color('black')
                autotext.set_fontweight('bold')
            
            ax.set_title(f'AQI Category Distribution - {city_name}', 
                        color='white', fontsize=16, fontweight='bold', pad=20)
            
            plt.tight_layout()
            return self.fig_to_base64(fig)
        except Exception as e:
            raise Exception(f"Error creating AQI categories pie chart: {str(e)}")
    
    def create_pollution_distribution_chart(self, df, city_name):
        """Create pollution level distribution chart"""
        try:
            fig, ax = plt.subplots(figsize=(10, 6), facecolor='#0f0f23', edgecolor='none')
            ax.set_facecolor('#1a1a2e')
            
            # Define pollution level ranges
            ranges = {
                'Very Low\n(0-50)': (0, 50),
                'Low\n(51-100)': (51, 100),
                'Medium\n(101-150)': (101, 150),
                'High\n(151-200)': (151, 200),
                'Very High\n(201-300)': (201, 300),
                'Extreme\n(301+)': (301, 1000)
            }
            
            counts = []
            colors = ['#00ff88', '#ffff00', '#ff8800', '#ff0000', '#8800ff', '#880000']
            
            for (min_val, max_val) in ranges.values():
                if max_val == 1000:  # Handle "301+" case
                    count = len(df[(df['aqiValue'] >= min_val)])
                else:
                    count = len(df[(df['aqiValue'] >= min_val) & (df['aqiValue'] <= max_val)])
                counts.append(count)
            
            bars = ax.bar(list(ranges.keys()), counts, color=colors, alpha=0.8, edgecolor='white', linewidth=1)
            
            # Add value labels on bars
            for bar, count in zip(bars, counts):
                if count > 0:
                    ax.text(bar.get_x() + bar.get_width()/2, bar.get_height() + max(counts)*0.01,
                           str(count), ha='center', va='bottom', color='white', fontweight='bold')
            
            ax.set_xlabel('Pollution Levels', color='white', fontsize=12)
            ax.set_ylabel('Number of Readings', color='white', fontsize=12)
            ax.set_title(f'Pollution Level Distribution - {city_name}', color='white', fontsize=16, fontweight='bold')
            ax.grid(True, alpha=0.3, axis='y', color='white')
            ax.tick_params(colors='white')
            
            plt.tight_layout()
            return self.fig_to_base64(fig)
        except Exception as e:
            raise Exception(f"Error creating pollution distribution chart: {str(e)}")
    
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
    
    def generate_enhanced_pdf_report(self, data_input, city_name, start_date, end_date):
        """Generate enhanced PDF report with charts and analytics"""
        try:
            # Process data
            df = self.process_data(data_input)
            stats = self.calculate_statistics(df)
            
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
            title = Paragraph(f"Air Quality Analytics Report - {city_name}", title_style)
            content.append(title)
            
            # Report info
            report_info = f"""
            <b>Report Generated:</b> {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}<br/>
            <b>Analysis Period:</b> {stats['start_date']} to {stats['end_date']}<br/>
            <b>Duration:</b> {stats['duration_hours']} hours<br/>
            <b>Data Points:</b> {stats['total_records']} readings<br/>
            <b>Generated by:</b> AirSight Analytics System
            """
            content.append(Paragraph(report_info, styles['Normal']))
            content.append(Spacer(1, 20))
            
            # Executive Summary
            content.append(Paragraph("Executive Summary", heading_style))
            
            summary_text = f"""
            This report provides a comprehensive analysis of air quality data for {city_name} 
            over a {stats['duration_hours']}-hour period. The analysis includes {stats['total_records']} 
            air quality readings with an average AQI of {stats['avg_aqi']}, ranging from 
            {stats['min_aqi']} to {stats['max_aqi']}. The overall trend shows {stats['trend'].lower()} 
            air quality levels during the analysis period.
            """
            content.append(Paragraph(summary_text, styles['Normal']))
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
            content.append(Spacer(1, 30))
            
            # Pollutant Averages Table
            if any(val is not None for val in stats['pollutant_averages'].values()):
                content.append(Paragraph("Average Pollutant Concentrations", heading_style))
                
                pollutant_data = [['Pollutant', 'Average (µg/m³)', 'Unit']]
                pollutant_units = {
                    'pm25': 'µg/m³', 'pm10': 'µg/m³', 'no2': 'µg/m³',
                    'so2': 'µg/m³', 'co': 'mg/m³', 'o3': 'µg/m³'
                }
                
                for pollutant, value in stats['pollutant_averages'].items():
                    if value is not None:
                        pollutant_name = pollutant.upper().replace('PM', 'PM')
                        unit = pollutant_units.get(pollutant, 'µg/m³')
                        pollutant_data.append([pollutant_name, f"{value:.2f}", unit])
                
                if len(pollutant_data) > 1:  # If we have data beyond header
                    pollutant_table = Table(pollutant_data, colWidths=[2*inch, 2*inch, 2*inch])
                    pollutant_table.setStyle(TableStyle([
                        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#2980b9')),
                        ('TEXTCOLOR', (0, 0), (-1, 0), colors.whitesmoke),
                        ('ALIGN', (0, 0), (-1, -1), 'CENTER'),
                        ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
                        ('FONTSIZE', (0, 0), (-1, 0), 12),
                        ('BOTTOMPADDING', (0, 0), (-1, 0), 12),
                        ('BACKGROUND', (0, 1), (-1, -1), colors.lightblue),
                        ('GRID', (0, 0), (-1, -1), 1, colors.black)
                    ]))
                    content.append(pollutant_table)
                    content.append(Spacer(1, 20))
            
            # Page break before charts
            content.append(PageBreak())
            
            # Charts Section
            content.append(Paragraph("Data Visualization", heading_style))
            
            try:
                # AQI Trend Chart
                content.append(Paragraph("AQI Trend Over Time", styles['Heading3']))
                aqi_trend_b64 = self.create_aqi_trend_chart(df, city_name)
                aqi_trend_img = self.base64_to_reportlab_image(aqi_trend_b64, width=7*inch, height=4*inch)
                content.append(aqi_trend_img)
                content.append(Spacer(1, 20))
                
                # Pollutants Bar Chart
                content.append(Paragraph("Average Pollutant Levels", styles['Heading3']))
                pollutants_b64 = self.create_pollutants_bar_chart(df, city_name)
                pollutants_img = self.base64_to_reportlab_image(pollutants_b64, width=6*inch, height=4*inch)
                content.append(pollutants_img)
                content.append(PageBreak())
                
                # AQI Categories Pie Chart
                content.append(Paragraph("AQI Category Distribution", styles['Heading3']))
                pie_chart_b64 = self.create_aqi_categories_pie_chart(df, city_name)
                pie_chart_img = self.base64_to_reportlab_image(pie_chart_b64, width=6*inch, height=5*inch)
                content.append(pie_chart_img)
                content.append(Spacer(1, 20))
                
                # Pollution Distribution Chart
                content.append(Paragraph("Pollution Level Distribution", styles['Heading3']))
                dist_chart_b64 = self.create_pollution_distribution_chart(df, city_name)
                dist_chart_img = self.base64_to_reportlab_image(dist_chart_b64, width=6*inch, height=4*inch)
                content.append(dist_chart_img)
                
            except Exception as chart_error:
                content.append(Paragraph(f"Note: Some charts could not be generated due to: {str(chart_error)}", styles['Normal']))
            
            # Health Recommendations
            content.append(PageBreak())
            content.append(Paragraph("Health Recommendations", heading_style))
            
            avg_aqi = stats['avg_aqi']
            if avg_aqi <= 50:
                health_text = """
                <b>Good Air Quality (0-50 AQI)</b><br/>
                • Air quality is considered satisfactory<br/>
                • Air pollution poses little or no risk<br/>
                • Enjoy outdoor activities as normal<br/>
                • No health precautions necessary for the general population
                """
            elif avg_aqi <= 100:
                health_text = """
                <b>Moderate Air Quality (51-100 AQI)</b><br/>
                • Air quality is acceptable for most people<br/>
                • Sensitive individuals may experience minor symptoms<br/>
                • Consider reducing prolonged outdoor exertion if you're sensitive<br/>
                • Monitor air quality if you have respiratory conditions
                """
            elif avg_aqi <= 150:
                health_text = """
                <b>Unhealthy for Sensitive Groups (101-150 AQI)</b><br/>
                • Sensitive groups may experience health effects<br/>
                • General public is not likely to be affected<br/>
                • People with heart/lung disease, children, and older adults should limit outdoor activities<br/>
                • Consider wearing a mask when outdoors
                """
            else:
                health_text = """
                <b>Unhealthy Air Quality (151+ AQI)</b><br/>
                • Everyone may begin to experience health effects<br/>
                • Sensitive groups may experience more serious effects<br/>
                • Avoid prolonged outdoor activities<br/>
                • Consider staying indoors and using air purifiers<br/>
                • Wear N95 masks when going outside
                """
            
            content.append(Paragraph(health_text, styles['Normal']))
            
            # Footer
            content.append(Spacer(1, 30))
            footer_text = """
            <i>This report was generated by the AirSight Analytics System. 
            Data sources include real-time air quality monitoring stations. 
            For the most current information, please visit our dashboard.</i>
            """
            content.append(Paragraph(footer_text, styles['Italic']))
            
            # Build PDF
            doc.build(content)
            
            # Get PDF data
            pdf_data = buffer.getvalue()
            buffer.close()
            
            return pdf_data
            
        except Exception as e:
            raise Exception(f"Error generating enhanced PDF report: {str(e)}")

    def process_real_time_data(self, data_input):
        """Process real-time data from OpenAQ API or JSON string"""
        try:
            # Handle JSON string input (from Java service)
            if isinstance(data_input, str) and data_input.startswith('['):
                data = json.loads(data_input)
            elif isinstance(data_input, str) and os.path.isfile(data_input):
                with open(data_input, 'r') as f:
                    data = json.load(f)
            else:
                data = data_input if isinstance(data_input, list) else json.loads(str(data_input))
            
            # Convert to DataFrame
            df = pd.DataFrame(data)
            
            # Handle different timestamp formats
            if 'timestamp' in df.columns:
                df['timestamp'] = pd.to_datetime(df['timestamp'], errors='coerce')
            else:
                df['timestamp'] = pd.to_datetime('now')
            
            df = df.sort_values('timestamp')
            
            # Ensure required columns exist
            required_cols = ['aqiValue', 'city']
            for col in required_cols:
                if col not in df.columns:
                    if col == 'aqiValue':
                        # Calculate AQI from PM2.5 if available
                        if 'pm25' in df.columns:
                            df['aqiValue'] = df['pm25'].apply(lambda x: self.calculate_aqi_from_pm25(x) if pd.notnull(x) else 50)
                        else:
                            df['aqiValue'] = 50  # Default value
                    elif col == 'city':
                        df['city'] = 'Unknown'
            
            # Add derived columns
            df['aqi_category'] = df['aqiValue'].apply(lambda x: self.get_aqi_category(x)[0])
            df['aqi_color'] = df['aqiValue'].apply(lambda x: self.get_aqi_category(x)[1])
            
            # Handle missing pollutant values
            pollutant_columns = ['pm25', 'pm10', 'no2', 'so2', 'co', 'o3']
            for col in pollutant_columns:
                if col in df.columns:
                    df[col] = pd.to_numeric(df[col], errors='coerce')
                else:
                    df[col] = None
            
            return df
        except Exception as e:
            raise Exception(f"Error processing real-time data: {str(e)}")
    
    def calculate_aqi_from_pm25(self, pm25):
        """Calculate AQI from PM2.5 concentration using EPA standard"""
        if pd.isna(pm25) or pm25 < 0:
            return 50
        
        # EPA breakpoints for PM2.5
        breakpoints = [
            (0, 12.0, 0, 50),
            (12.1, 35.4, 51, 100),
            (35.5, 55.4, 101, 150),
            (55.5, 150.4, 151, 200),
            (150.5, 250.4, 201, 300),
            (250.5, 350.4, 301, 400),
            (350.5, 500.4, 401, 500)
        ]
        
        for bp_lo, bp_hi, aqi_lo, aqi_hi in breakpoints:
            if bp_lo <= pm25 <= bp_hi:
                aqi = ((aqi_hi - aqi_lo) / (bp_hi - bp_lo)) * (pm25 - bp_lo) + aqi_lo
                return int(round(aqi))
        
        return 500  # Hazardous

def main():
    """Main function to handle command line usage"""
    try:
        if len(sys.argv) < 4:
            print("Usage: python analytics_service.py <data_json_or_file> <city_name> <operation> [start_date] [end_date]")
            print("Operations: stats, pdf, trend_chart, bar_chart, pie_chart, dist_chart")
            sys.exit(1)
        
        data_input = sys.argv[1]  # Can be JSON string or file path
        city_name = sys.argv[2]
        operation = sys.argv[3]
        
        start_date = sys.argv[4] if len(sys.argv) > 4 else None
        end_date = sys.argv[5] if len(sys.argv) > 5 else None
        
        analytics = AirQualityAnalytics()
        
        # Use the new process_real_time_data method for better handling
        if operation == "stats":
            df = analytics.process_real_time_data(data_input)
            stats = analytics.calculate_statistics(df)
            print(json.dumps(stats, indent=2))
            
        elif operation == "pdf":
            df = analytics.process_real_time_data(data_input)
            # For PDF, we need to use the data directly
            pdf_data = analytics.generate_enhanced_pdf_report(data_input, city_name, start_date, end_date)
            # For Java integration, write PDF data to stdout as binary
            sys.stdout.buffer.write(pdf_data)
            return
            
        elif operation == "trend_chart":
            df = analytics.process_real_time_data(data_input)
            chart_b64 = analytics.create_aqi_trend_chart(df, city_name)
            print(chart_b64)
            
        elif operation == "bar_chart":
            df = analytics.process_real_time_data(data_input)
            chart_b64 = analytics.create_pollutants_bar_chart(df, city_name)
            print(chart_b64)
            
        elif operation == "pie_chart":
            df = analytics.process_real_time_data(data_input)
            chart_b64 = analytics.create_aqi_categories_pie_chart(df, city_name)
            print(chart_b64)
            
        elif operation == "dist_chart":
            df = analytics.process_real_time_data(data_input)
            chart_b64 = analytics.create_pollution_distribution_chart(df, city_name)
            print(chart_b64)
            
        else:
            print(f"Unknown operation: {operation}")
            sys.exit(1)
            
    except Exception as e:
        print(f"Error: {str(e)}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
