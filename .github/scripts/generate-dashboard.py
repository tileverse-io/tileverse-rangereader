#!/usr/bin/env python3
"""
Dashboard Generator for Requirement Compliance

This script generates an HTML dashboard for visualizing requirement compliance status.
"""

import argparse
import sys
from pathlib import Path
from datetime import datetime
import json
import re

class DashboardGenerator:
    """Generates HTML dashboard from compliance reports."""
    
    def __init__(self):
        self.compliance_data = {}
    
    def parse_compliance_report(self, report_file: Path) -> None:
        """Parse the compliance report markdown file."""
        content = report_file.read_text()
        
        # Extract key metrics
        self.compliance_data = {
            'timestamp': self._extract_timestamp(content),
            'overall_status': self._extract_overall_status(content),
            'summary': self._extract_summary(content),
            'categories': self._extract_categories(content),
            'risks': self._extract_risks(content),
            'recommendations': self._extract_recommendations(content)
        }
    
    def _extract_timestamp(self, content: str) -> str:
        """Extract timestamp from report."""
        match = re.search(r'\*\*Generated\*\*:\s*([^\n]+)', content)
        return match.group(1) if match else datetime.now().isoformat()
    
    def _extract_overall_status(self, content: str) -> str:
        """Extract overall status from report."""
        match = re.search(r'\*\*Overall Status\*\*:\s*([^\n]+)', content)
        return match.group(1) if match else 'UNKNOWN'
    
    def _extract_summary(self, content: str) -> dict:
        """Extract summary statistics from report."""
        summary = {}
        
        patterns = {
            'total_requirements': r'Total Requirements\*\*:\s*(\d+)',
            'implemented': r'Implemented\*\*:\s*(\d+)',
            'verified': r'Verified\*\*:\s*(\d+)',
            'planned': r'Planned\*\*:\s*(\d+)',
            'not_started': r'Not Started\*\*:\s*(\d+)'
        }
        
        for key, pattern in patterns.items():
            match = re.search(pattern, content)
            summary[key] = int(match.group(1)) if match else 0
        
        return summary
    
    def _extract_categories(self, content: str) -> dict:
        """Extract category compliance information."""
        categories = {}
        
        # Find the "Compliance by Category" section
        category_section = re.search(r'## Compliance by Category\s*\n\n(.*?)(?=##|$)', content, re.DOTALL)
        if not category_section:
            return categories
        
        section_content = category_section.group(1)
        
        # Extract each category
        category_pattern = r'### (\w+) Requirements ([^#]*?)(?=###|$)'
        matches = re.findall(category_pattern, section_content, re.DOTALL)
        
        for match in matches:
            category_name = match[0].lower()
            category_content = match[1]
            
            status_match = re.search(r'\*\*Status\*\*:\s*([^\n]+)', category_content)
            details_match = re.search(r'\*\*Details\*\*:\s*([^\n]+)', category_content)
            
            categories[category_name] = {
                'status': status_match.group(1) if status_match else 'UNKNOWN',
                'details': details_match.group(1) if details_match else ''
            }
        
        return categories
    
    def _extract_risks(self, content: str) -> list:
        """Extract risk information from report."""
        risks = []
        
        # Find the "Risk Assessment" section
        risk_section = re.search(r'## Risk Assessment\s*\n\n(.*?)(?=##|$)', content, re.DOTALL)
        if not risk_section:
            return risks
        
        section_content = risk_section.group(1)
        
        # Extract each risk
        risk_pattern = r'### ([^#]*?)\n\n\*\*Severity\*\*:\s*([^\n]+)\n\*\*Description\*\*:\s*([^\n]+)'
        matches = re.findall(risk_pattern, section_content, re.DOTALL)
        
        for match in matches:
            risks.append({
                'title': match[0].strip(),
                'severity': match[1].strip(),
                'description': match[2].strip()
            })
        
        return risks
    
    def _extract_recommendations(self, content: str) -> list:
        """Extract recommendations from report."""
        recommendations = []
        
        # Find the "Recommendations" section
        rec_section = re.search(r'## Recommendations\s*\n\n(.*?)(?=##|$)', content, re.DOTALL)
        if not rec_section:
            return recommendations
        
        section_content = rec_section.group(1)
        
        # Extract numbered recommendations
        rec_pattern = r'\d+\.\s*([^\n]+)'
        matches = re.findall(rec_pattern, section_content)
        
        return matches
    
    def generate_dashboard(self, output_file: Path) -> None:
        """Generate HTML dashboard."""
        html_content = self._generate_html()
        output_file.write_text(html_content)
    
    def _generate_html(self) -> str:
        """Generate the complete HTML dashboard."""
        return f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Requirement Compliance Dashboard</title>
    <style>
        {self._get_css_styles()}
    </style>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
</head>
<body>
    <div class="container">
        {self._generate_header()}
        {self._generate_summary_cards()}
        {self._generate_charts()}
        {self._generate_category_details()}
        {self._generate_risks_section()}
        {self._generate_recommendations_section()}
        {self._generate_footer()}
    </div>
    
    <script>
        {self._generate_javascript()}
    </script>
</body>
</html>"""
    
    def _get_css_styles(self) -> str:
        """Get CSS styles for the dashboard."""
        return """
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            color: #333;
        }
        
        .container {
            max-width: 1200px;
            margin: 0 auto;
            padding: 20px;
        }
        
        .header {
            background: white;
            border-radius: 15px;
            padding: 30px;
            margin-bottom: 30px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.1);
            text-align: center;
        }
        
        .header h1 {
            color: #2c3e50;
            margin-bottom: 10px;
            font-size: 2.5rem;
        }
        
        .status-badge {
            display: inline-block;
            padding: 8px 20px;
            border-radius: 25px;
            font-weight: bold;
            font-size: 1.1rem;
            margin-top: 10px;
        }
        
        .status-pass { background: #2ecc71; color: white; }
        .status-fail { background: #e74c3c; color: white; }
        .status-partial { background: #f39c12; color: white; }
        .status-unknown { background: #95a5a6; color: white; }
        
        .summary-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }
        
        .summary-card {
            background: white;
            border-radius: 15px;
            padding: 25px;
            box-shadow: 0 8px 25px rgba(0,0,0,0.1);
            text-align: center;
            transition: transform 0.3s ease;
        }
        
        .summary-card:hover {
            transform: translateY(-5px);
        }
        
        .summary-card h3 {
            color: #2c3e50;
            margin-bottom: 15px;
            font-size: 1.1rem;
        }
        
        .summary-card .number {
            font-size: 3rem;
            font-weight: bold;
            color: #3498db;
            display: block;
        }
        
        .charts-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
            gap: 30px;
            margin-bottom: 30px;
        }
        
        .chart-container {
            background: white;
            border-radius: 15px;
            padding: 25px;
            box-shadow: 0 8px 25px rgba(0,0,0,0.1);
        }
        
        .chart-container h3 {
            color: #2c3e50;
            margin-bottom: 20px;
            text-align: center;
        }
        
        .category-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }
        
        .category-card {
            background: white;
            border-radius: 15px;
            padding: 25px;
            box-shadow: 0 8px 25px rgba(0,0,0,0.1);
        }
        
        .category-card h3 {
            color: #2c3e50;
            margin-bottom: 15px;
            display: flex;
            align-items: center;
            gap: 10px;
        }
        
        .risks-section, .recommendations-section {
            background: white;
            border-radius: 15px;
            padding: 30px;
            margin-bottom: 30px;
            box-shadow: 0 8px 25px rgba(0,0,0,0.1);
        }
        
        .risk-item {
            background: #fff5f5;
            border-left: 4px solid #e74c3c;
            padding: 15px;
            margin-bottom: 15px;
            border-radius: 0 8px 8px 0;
        }
        
        .risk-high { border-left-color: #e74c3c; background: #fff5f5; }
        .risk-medium { border-left-color: #f39c12; background: #fefcf3; }
        .risk-low { border-left-color: #2ecc71; background: #f8fff8; }
        
        .recommendation-item {
            background: #f8f9fa;
            padding: 15px;
            margin-bottom: 10px;
            border-radius: 8px;
            border-left: 4px solid #3498db;
        }
        
        .footer {
            text-align: center;
            color: #fff;
            margin-top: 30px;
            opacity: 0.8;
        }
        
        .emoji {
            font-size: 1.2em;
        }
        """
    
    def _generate_header(self) -> str:
        """Generate header section."""
        overall_status = self.compliance_data.get('overall_status', 'UNKNOWN')
        timestamp = self.compliance_data.get('timestamp', 'Unknown')
        
        status_class = f"status-{overall_status.lower()}"
        
        return f"""
        <div class="header">
            <h1>🎯 Requirement Compliance Dashboard</h1>
            <p>Last Updated: {timestamp}</p>
            <div class="status-badge {status_class}">
                Overall Status: {overall_status}
            </div>
        </div>
        """
    
    def _generate_summary_cards(self) -> str:
        """Generate summary cards section."""
        summary = self.compliance_data.get('summary', {})
        
        return f"""
        <div class="summary-grid">
            <div class="summary-card">
                <h3>📋 Total Requirements</h3>
                <span class="number">{summary.get('total_requirements', 0)}</span>
            </div>
            <div class="summary-card">
                <h3>✅ Implemented</h3>
                <span class="number">{summary.get('implemented', 0)}</span>
            </div>
            <div class="summary-card">
                <h3>🔍 Verified</h3>
                <span class="number">{summary.get('verified', 0)}</span>
            </div>
            <div class="summary-card">
                <h3>📅 Planned</h3>
                <span class="number">{summary.get('planned', 0)}</span>
            </div>
            <div class="summary-card">
                <h3>⏸️ Not Started</h3>
                <span class="number">{summary.get('not_started', 0)}</span>
            </div>
        </div>
        """
    
    def _generate_charts(self) -> str:
        """Generate charts section."""
        return """
        <div class="charts-grid">
            <div class="chart-container">
                <h3>📊 Requirements Status Distribution</h3>
                <canvas id="statusChart" width="400" height="200"></canvas>
            </div>
            <div class="chart-container">
                <h3>🎯 Category Compliance</h3>
                <canvas id="categoryChart" width="400" height="200"></canvas>
            </div>
        </div>
        """
    
    def _generate_category_details(self) -> str:
        """Generate category details section."""
        categories = self.compliance_data.get('categories', {})
        
        category_cards = []
        for name, data in categories.items():
            status = data.get('status', 'UNKNOWN')
            details = data.get('details', '')
            
            emoji_map = {
                'functional': '⚙️',
                'quality': '🎯',
                'performance': '🚀',
                'security': '🔒'
            }
            
            status_emoji_map = {
                'PASS': '✅',
                'FAIL': '❌',
                'PARTIAL': '🟡',
                'PLANNED': '📋',
                'NOT_TESTED': '⚠️'
            }
            
            emoji = emoji_map.get(name, '📋')
            status_emoji = status_emoji_map.get(status, '❓')
            
            category_cards.append(f"""
            <div class="category-card">
                <h3>{emoji} {name.title()} Requirements {status_emoji}</h3>
                <p><strong>Status:</strong> {status}</p>
                <p><strong>Details:</strong> {details}</p>
            </div>
            """)
        
        return f"""
        <div class="category-grid">
            {''.join(category_cards)}
        </div>
        """
    
    def _generate_risks_section(self) -> str:
        """Generate risks section."""
        risks = self.compliance_data.get('risks', [])
        
        if not risks:
            return """
            <div class="risks-section">
                <h2>🛡️ Risk Assessment</h2>
                <p style="color: #2ecc71; font-weight: bold;">✅ No significant risks identified</p>
            </div>
            """
        
        risk_items = []
        for risk in risks:
            severity = risk.get('severity', 'medium').lower()
            severity_emoji = {'high': '🔴', 'medium': '🟡', 'low': '🟢'}.get(severity, '❓')
            
            risk_items.append(f"""
            <div class="risk-item risk-{severity}">
                <h4>{severity_emoji} {risk.get('title', 'Unknown Risk')}</h4>
                <p><strong>Severity:</strong> {risk.get('severity', 'Unknown').title()}</p>
                <p>{risk.get('description', 'No description available')}</p>
            </div>
            """)
        
        return f"""
        <div class="risks-section">
            <h2>⚠️ Risk Assessment</h2>
            {''.join(risk_items)}
        </div>
        """
    
    def _generate_recommendations_section(self) -> str:
        """Generate recommendations section."""
        recommendations = self.compliance_data.get('recommendations', [])
        
        if not recommendations:
            return """
            <div class="recommendations-section">
                <h2>💡 Recommendations</h2>
                <p style="color: #2ecc71; font-weight: bold;">✅ No specific recommendations at this time</p>
            </div>
            """
        
        rec_items = []
        for i, rec in enumerate(recommendations, 1):
            rec_items.append(f"""
            <div class="recommendation-item">
                <strong>{i}.</strong> {rec}
            </div>
            """)
        
        return f"""
        <div class="recommendations-section">
            <h2>💡 Recommendations</h2>
            {''.join(rec_items)}
        </div>
        """
    
    def _generate_footer(self) -> str:
        """Generate footer section."""
        return """
        <div class="footer">
            <p>🤖 Generated automatically by the Tileverse Range Reader requirement verification system</p>
        </div>
        """
    
    def _generate_javascript(self) -> str:
        """Generate JavaScript for charts."""
        summary = self.compliance_data.get('summary', {})
        categories = self.compliance_data.get('categories', {})
        
        # Prepare data for status chart
        status_data = [
            summary.get('implemented', 0),
            summary.get('verified', 0),
            summary.get('planned', 0),
            summary.get('not_started', 0)
        ]
        
        # Prepare data for category chart
        category_labels = list(categories.keys())
        category_statuses = []
        for cat_data in categories.values():
            status = cat_data.get('status', 'UNKNOWN')
            if status == 'PASS':
                category_statuses.append(100)
            elif status == 'FAIL':
                category_statuses.append(0)
            elif status == 'PARTIAL':
                category_statuses.append(50)
            else:
                category_statuses.append(25)
        
        return f"""
        // Status Distribution Chart
        const statusCtx = document.getElementById('statusChart').getContext('2d');
        new Chart(statusCtx, {{
            type: 'doughnut',
            data: {{
                labels: ['Implemented', 'Verified', 'Planned', 'Not Started'],
                datasets: [{{
                    data: {status_data},
                    backgroundColor: [
                        '#2ecc71',
                        '#3498db',
                        '#f39c12',
                        '#e74c3c'
                    ],
                    borderWidth: 2,
                    borderColor: '#fff'
                }}]
            }},
            options: {{
                responsive: true,
                maintainAspectRatio: false,
                plugins: {{
                    legend: {{
                        position: 'bottom'
                    }}
                }}
            }}
        }});
        
        // Category Compliance Chart
        const categoryCtx = document.getElementById('categoryChart').getContext('2d');
        new Chart(categoryCtx, {{
            type: 'bar',
            data: {{
                labels: {category_labels},
                datasets: [{{
                    label: 'Compliance %',
                    data: {category_statuses},
                    backgroundColor: [
                        '#3498db',
                        '#2ecc71',
                        '#f39c12',
                        '#e74c3c'
                    ],
                    borderWidth: 1
                }}]
            }},
            options: {{
                responsive: true,
                maintainAspectRatio: false,
                scales: {{
                    y: {{
                        beginAtZero: true,
                        max: 100,
                        ticks: {{
                            callback: function(value) {{
                                return value + '%';
                            }}
                        }}
                    }}
                }},
                plugins: {{
                    legend: {{
                        display: false
                    }}
                }}
            }}
        }});
        """

def main():
    parser = argparse.ArgumentParser(description='Generate HTML dashboard from compliance report')
    parser.add_argument('--compliance-report', required=True, help='Compliance report markdown file')
    parser.add_argument('--output', required=True, help='Output HTML dashboard file')
    
    args = parser.parse_args()
    
    report_file = Path(args.compliance_report)
    output_file = Path(args.output)
    
    if not report_file.exists():
        print(f"Error: Compliance report file {report_file} not found")
        sys.exit(1)
    
    generator = DashboardGenerator()
    generator.parse_compliance_report(report_file)
    generator.generate_dashboard(output_file)
    
    print(f"Dashboard generated successfully: {output_file}")

if __name__ == '__main__':
    main()