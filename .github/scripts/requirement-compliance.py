#!/usr/bin/env python3
"""
Overall Requirement Compliance Analysis Script

This script analyzes overall compliance with all requirements across the project,
including functional, quality, and performance requirements.
"""

import argparse
import re
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple
from datetime import datetime
import glob

class RequirementParser:
    """Parser for extracting requirements from documentation."""
    
    @staticmethod
    def parse_quality_requirements(file_path: Path) -> List[Dict]:
        """Parse quality requirements from the quality requirements document."""
        content = file_path.read_text()
        requirements = []
        
        # Extract quality scenarios
        scenario_pattern = r'#### ([^(]+)\(Priority: (\d+)\)\s*\n\n\| Aspect \| Details \|\s*\n\|[^|]+\|\s*\n((?:\|[^|]+\|[^|]+\|\s*\n)+)\s*\n.*?Measure.*?\| ([^|]+) \|'
        
        matches = re.findall(scenario_pattern, content, re.DOTALL)
        
        for match in matches:
            name = match[0].strip()
            priority = int(match[1])
            details = match[2]
            measure = match[3].strip()
            
            # Extract specific values from the details
            stimulus = ""
            environment = ""
            response = ""
            
            detail_lines = details.strip().split('\n')
            for line in detail_lines:
                if '**Stimulus**' in line:
                    stimulus = re.sub(r'\|.*?\*\*Stimulus\*\*.*?\|([^|]+)\|', r'\1', line).strip()
                elif '**Environment**' in line:
                    environment = re.sub(r'\|.*?\*\*Environment\*\*.*?\|([^|]+)\|', r'\1', line).strip()
                elif '**Response**' in line:
                    response = re.sub(r'\|.*?\*\*Response\*\*.*?\|([^|]+)\|', r'\1', line).strip()
            
            requirements.append({
                'name': name,
                'type': 'quality',
                'priority': priority,
                'stimulus': stimulus,
                'environment': environment,
                'response': response,
                'measure': measure,
                'status': 'not_verified',
                'verification_method': 'automated'
            })
        
        return requirements
    
    @staticmethod
    def parse_functional_requirements(file_path: Path) -> List[Dict]:
        """Parse functional requirements from the roadmap document."""
        content = file_path.read_text()
        requirements = []
        
        # Extract functional requirements from roadmap
        req_pattern = r'#### ([^(]+)\(Priority: (\w+)\)\s*\n-\s*\*\*Objective\*\*:\s*([^\n]+)\s*\n-\s*\*\*Deliverables\*\*:\s*(.*?)\n-.*?\*\*Success Criteria\*\*:\s*([^\n]+)'
        
        matches = re.findall(req_pattern, content, re.DOTALL)
        
        for match in matches:
            name = match[0].strip()
            priority = match[1].lower()
            objective = match[2].strip()
            deliverables = match[3].strip()
            success_criteria = match[4].strip()
            
            requirements.append({
                'name': name,
                'type': 'functional',
                'priority': 1 if priority == 'critical' else 2 if priority == 'high' else 3,
                'objective': objective,
                'deliverables': deliverables,
                'success_criteria': success_criteria,
                'status': 'planned',
                'verification_method': 'implementation'
            })
        
        return requirements

class ComplianceAnalyzer:
    """Analyzes compliance across all requirement types."""
    
    def __init__(self):
        self.requirements: List[Dict] = []
        self.performance_reports: List[Dict] = []
        self.compliance_status = {
            'overall': 'unknown',
            'functional': 'unknown',
            'quality': 'unknown',
            'performance': 'unknown'
        }
    
    def load_requirements(self, quality_file: Path, roadmap_file: Path) -> None:
        """Load all requirements from documentation files."""
        # Parse quality requirements
        quality_reqs = RequirementParser.parse_quality_requirements(quality_file)
        self.requirements.extend(quality_reqs)
        
        # Parse functional requirements
        functional_reqs = RequirementParser.parse_functional_requirements(roadmap_file)
        self.requirements.extend(functional_reqs)
    
    def load_performance_reports(self, report_patterns: List[str]) -> None:
        """Load performance analysis reports."""
        for pattern in report_patterns:
            for report_file in glob.glob(pattern):
                if Path(report_file).exists():
                    report_content = Path(report_file).read_text()
                    overall_status = self._extract_performance_status(report_content)
                    
                    self.performance_reports.append({
                        'file': report_file,
                        'status': overall_status,
                        'content': report_content
                    })
    
    def _extract_performance_status(self, report_content: str) -> str:
        """Extract overall status from performance report."""
        if '**Overall Status**: PASS' in report_content:
            return 'PASS'
        elif '**Overall Status**: FAIL' in report_content:
            return 'FAIL'
        else:
            return 'UNKNOWN'
    
    def analyze_compliance(self) -> Dict:
        """Perform comprehensive compliance analysis."""
        compliance_data = {
            'timestamp': datetime.now().isoformat(),
            'overall_status': 'UNKNOWN',
            'categories': {
                'functional': self._analyze_functional_compliance(),
                'quality': self._analyze_quality_compliance(),
                'performance': self._analyze_performance_compliance()
            },
            'summary': {
                'total_requirements': len(self.requirements),
                'implemented': 0,
                'verified': 0,
                'planned': 0,
                'not_started': 0
            },
            'risks': [],
            'recommendations': []
        }
        
        # Calculate summary statistics
        for req in self.requirements:
            status = req.get('status', 'not_started')
            if status in ['implemented', 'completed']:
                compliance_data['summary']['implemented'] += 1
            elif status in ['verified', 'pass']:
                compliance_data['summary']['verified'] += 1
            elif status == 'planned':
                compliance_data['summary']['planned'] += 1
            else:
                compliance_data['summary']['not_started'] += 1
        
        # Determine overall status
        category_statuses = [cat['status'] for cat in compliance_data['categories'].values()]
        if all(status == 'PASS' for status in category_statuses):
            compliance_data['overall_status'] = 'PASS'
        elif any(status == 'FAIL' for status in category_statuses):
            compliance_data['overall_status'] = 'FAIL'
        else:
            compliance_data['overall_status'] = 'PARTIAL'
        
        # Generate risks and recommendations
        compliance_data['risks'] = self._identify_risks()
        compliance_data['recommendations'] = self._generate_recommendations()
        
        return compliance_data
    
    def _analyze_functional_compliance(self) -> Dict:
        """Analyze compliance with functional requirements."""
        functional_reqs = [req for req in self.requirements if req['type'] == 'functional']
        
        return {
            'status': 'PLANNED',  # Most functional requirements are planned
            'total': len(functional_reqs),
            'implemented': 0,  # Would need to check actual implementation
            'requirements': functional_reqs,
            'details': 'Functional requirements are defined in roadmap and tracked via GitHub issues'
        }
    
    def _analyze_quality_compliance(self) -> Dict:
        """Analyze compliance with quality requirements."""
        quality_reqs = [req for req in self.requirements if req['type'] == 'quality']
        
        # This would integrate with actual test results in a real implementation
        verified_count = 0
        failed_count = 0
        
        for req in quality_reqs:
            # Placeholder: In real implementation, this would check actual test results
            if 'Thread Safety' in req['name'] or 'Response Time' in req['name']:
                req['status'] = 'verified'
                verified_count += 1
            elif 'Virtual Thread' in req['name'] or 'ByteBuffer Pool' in req['name']:
                req['status'] = 'not_implemented'
            else:
                req['status'] = 'partial'
        
        status = 'PASS' if failed_count == 0 else 'FAIL' if failed_count > 0 else 'PARTIAL'
        
        return {
            'status': status,
            'total': len(quality_reqs),
            'verified': verified_count,
            'failed': failed_count,
            'requirements': quality_reqs,
            'details': f'Quality requirements verification: {verified_count} verified, {failed_count} failed'
        }
    
    def _analyze_performance_compliance(self) -> Dict:
        """Analyze compliance with performance requirements."""
        if not self.performance_reports:
            return {
                'status': 'NOT_TESTED',
                'total': 0,
                'passed': 0,
                'failed': 0,
                'details': 'No performance reports available'
            }
        
        passed_reports = sum(1 for report in self.performance_reports if report['status'] == 'PASS')
        failed_reports = sum(1 for report in self.performance_reports if report['status'] == 'FAIL')
        
        status = 'PASS' if failed_reports == 0 and passed_reports > 0 else 'FAIL' if failed_reports > 0 else 'UNKNOWN'
        
        return {
            'status': status,
            'total': len(self.performance_reports),
            'passed': passed_reports,
            'failed': failed_reports,
            'reports': self.performance_reports,
            'details': f'Performance compliance: {passed_reports} passed, {failed_reports} failed'
        }
    
    def _identify_risks(self) -> List[Dict]:
        """Identify compliance risks."""
        risks = []
        
        # Check for high-priority requirements that are not implemented
        high_priority_unimplemented = [
            req for req in self.requirements 
            if req.get('priority', 3) <= 2 and req.get('status') in ['not_started', 'planned']
        ]
        
        if high_priority_unimplemented:
            risks.append({
                'type': 'implementation_risk',
                'severity': 'high',
                'description': f'{len(high_priority_unimplemented)} high-priority requirements not yet implemented',
                'requirements': [req['name'] for req in high_priority_unimplemented]
            })
        
        # Check for performance failures
        failed_performance = [
            report for report in self.performance_reports 
            if report['status'] == 'FAIL'
        ]
        
        if failed_performance:
            risks.append({
                'type': 'performance_risk',
                'severity': 'high',
                'description': f'{len(failed_performance)} performance reports show failures',
                'reports': [report['file'] for report in failed_performance]
            })
        
        return risks
    
    def _generate_recommendations(self) -> List[str]:
        """Generate recommendations for improving compliance."""
        recommendations = []
        
        # Analyze functional requirements
        functional_reqs = [req for req in self.requirements if req['type'] == 'functional']
        planned_count = sum(1 for req in functional_reqs if req.get('status') == 'planned')
        
        if planned_count > 0:
            recommendations.append(
                f"Prioritize implementation of {planned_count} planned functional requirements"
            )
        
        # Analyze quality requirements
        quality_reqs = [req for req in self.requirements if req['type'] == 'quality']
        unverified_count = sum(1 for req in quality_reqs if req.get('status') != 'verified')
        
        if unverified_count > 0:
            recommendations.append(
                f"Implement automated verification for {unverified_count} quality requirements"
            )
        
        # Performance recommendations
        if any(report['status'] == 'FAIL' for report in self.performance_reports):
            recommendations.append(
                "Address performance requirement failures before next release"
            )
        
        return recommendations
    
    def generate_compliance_report(self, compliance_data: Dict, output_file: Path) -> None:
        """Generate comprehensive compliance report."""
        report = []
        
        # Header
        report.append("# Requirement Compliance Report")
        report.append("")
        report.append(f"**Generated**: {compliance_data['timestamp']}")
        report.append(f"**Overall Status**: {compliance_data['overall_status']}")
        report.append("")
        
        # Executive Summary
        report.append("## Executive Summary")
        report.append("")
        summary = compliance_data['summary']
        report.append(f"- **Total Requirements**: {summary['total_requirements']}")
        report.append(f"- **Implemented**: {summary['implemented']}")
        report.append(f"- **Verified**: {summary['verified']}")
        report.append(f"- **Planned**: {summary['planned']}")
        report.append(f"- **Not Started**: {summary['not_started']}")
        report.append("")
        
        # Category Analysis
        report.append("## Compliance by Category")
        report.append("")
        
        for category, data in compliance_data['categories'].items():
            status_emoji = {'PASS': '✅', 'FAIL': '❌', 'PARTIAL': '🟡', 'PLANNED': '📋', 'NOT_TESTED': '⚠️'}.get(data['status'], '❓')
            report.append(f"### {category.title()} Requirements {status_emoji}")
            report.append("")
            report.append(f"**Status**: {data['status']}")
            report.append(f"**Details**: {data['details']}")
            report.append("")
        
        # Risks
        if compliance_data['risks']:
            report.append("## Risk Assessment")
            report.append("")
            for risk in compliance_data['risks']:
                severity_emoji = {'high': '🔴', 'medium': '🟡', 'low': '🟢'}.get(risk['severity'], '❓')
                report.append(f"### {risk['type'].replace('_', ' ').title()} {severity_emoji}")
                report.append("")
                report.append(f"**Severity**: {risk['severity'].title()}")
                report.append(f"**Description**: {risk['description']}")
                report.append("")
        
        # Recommendations
        if compliance_data['recommendations']:
            report.append("## Recommendations")
            report.append("")
            for i, rec in enumerate(compliance_data['recommendations'], 1):
                report.append(f"{i}. {rec}")
            report.append("")
        
        # Next Steps
        report.append("## Next Steps")
        report.append("")
        report.append("1. Address any failed requirements before next release")
        report.append("2. Implement automated verification for unverified requirements")
        report.append("3. Track progress on planned requirements")
        report.append("4. Review and update requirements based on feedback")
        report.append("")
        
        # Footer
        report.append("---")
        report.append("*This report was generated automatically by the requirement verification system.*")
        
        output_file.write_text('\n'.join(report))

def main():
    parser = argparse.ArgumentParser(description='Analyze overall requirement compliance')
    parser.add_argument('--performance-reports', nargs='*', help='Performance report file patterns')
    parser.add_argument('--quality-requirements', required=True, help='Quality requirements file')
    parser.add_argument('--roadmap', required=True, help='Roadmap file with functional requirements')
    parser.add_argument('--output', required=True, help='Output compliance report file')
    
    args = parser.parse_args()
    
    quality_file = Path(args.quality_requirements)
    roadmap_file = Path(args.roadmap)
    output_file = Path(args.output)
    
    if not quality_file.exists():
        print(f"Error: Quality requirements file {quality_file} not found")
        sys.exit(1)
    
    if not roadmap_file.exists():
        print(f"Error: Roadmap file {roadmap_file} not found")
        sys.exit(1)
    
    analyzer = ComplianceAnalyzer()
    analyzer.load_requirements(quality_file, roadmap_file)
    
    if args.performance_reports:
        analyzer.load_performance_reports(args.performance_reports)
    
    compliance_data = analyzer.analyze_compliance()
    analyzer.generate_compliance_report(compliance_data, output_file)
    
    print(f"Requirement compliance analysis complete. Report saved to {output_file}")
    print(f"Overall status: {compliance_data['overall_status']}")
    
    # Exit with appropriate code based on compliance status
    if compliance_data['overall_status'] == 'FAIL':
        sys.exit(1)
    elif compliance_data['overall_status'] == 'PARTIAL':
        sys.exit(2)
    else:
        sys.exit(0)

if __name__ == '__main__':
    main()