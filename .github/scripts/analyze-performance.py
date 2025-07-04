#!/usr/bin/env python3
"""
Performance Analysis Script for Requirement Verification

This script analyzes JMH benchmark results against defined performance requirements
and generates compliance reports.
"""

import json
import argparse
import re
import sys
from pathlib import Path
from typing import Dict, List, Tuple, Optional
import yaml

class PerformanceRequirement:
    def __init__(self, name: str, metric: str, target: float, unit: str, priority: str):
        self.name = name
        self.metric = metric
        self.target = target
        self.unit = unit
        self.priority = priority

class BenchmarkResult:
    def __init__(self, benchmark: str, mode: str, score: float, unit: str, error: float = 0.0):
        self.benchmark = benchmark
        self.mode = mode
        self.score = score
        self.unit = unit
        self.error = error

class PerformanceAnalyzer:
    def __init__(self):
        self.requirements: List[PerformanceRequirement] = []
        self.results: List[BenchmarkResult] = []
        
    def parse_requirements(self, requirements_file: Path) -> None:
        """Parse performance requirements from markdown documentation."""
        content = requirements_file.read_text()
        
        # Extract performance requirements using regex
        patterns = {
            'response_time': r'Response Time.*?< (\d+)ms',
            'throughput': r'Throughput.*?> (\d+) requests/second',
            'memory_usage': r'Memory Usage.*?< (\d+)MB',
            'cache_hit_ratio': r'Cache Hit Ratio.*?> (\d+)%',
            'concurrent_ops': r'(\d+,?\d*)\+ concurrent operations'
        }
        
        for req_type, pattern in patterns.items():
            matches = re.findall(pattern, content, re.IGNORECASE)
            for match in matches:
                value = float(match.replace(',', ''))
                unit = self._get_unit_for_requirement(req_type)
                self.requirements.append(
                    PerformanceRequirement(req_type, req_type, value, unit, 'high')
                )
    
    def _get_unit_for_requirement(self, req_type: str) -> str:
        """Get the appropriate unit for a requirement type."""
        units = {
            'response_time': 'ms',
            'throughput': 'ops/sec',
            'memory_usage': 'MB',
            'cache_hit_ratio': '%',
            'concurrent_ops': 'ops'
        }
        return units.get(req_type, 'unknown')
    
    def parse_benchmark_results(self, results_file: Path) -> None:
        """Parse JMH benchmark results from JSON file."""
        with open(results_file) as f:
            data = json.load(f)
        
        for result in data:
            benchmark_name = result.get('benchmark', '')
            mode = result.get('mode', '')
            score = result.get('primaryMetric', {}).get('score', 0.0)
            unit = result.get('primaryMetric', {}).get('scoreUnit', '')
            error = result.get('primaryMetric', {}).get('scoreError', 0.0)
            
            self.results.append(BenchmarkResult(benchmark_name, mode, score, unit, error))
    
    def analyze_compliance(self) -> Dict[str, Dict]:
        """Analyze benchmark results against requirements."""
        compliance_report = {
            'overall_status': 'PASS',
            'requirements': [],
            'summary': {
                'total_requirements': len(self.requirements),
                'passed': 0,
                'failed': 0,
                'not_tested': 0
            }
        }
        
        for requirement in self.requirements:
            compliance = self._check_requirement_compliance(requirement)
            compliance_report['requirements'].append(compliance)
            
            if compliance['status'] == 'PASS':
                compliance_report['summary']['passed'] += 1
            elif compliance['status'] == 'FAIL':
                compliance_report['summary']['failed'] += 1
                if requirement.priority == 'high':
                    compliance_report['overall_status'] = 'FAIL'
            else:
                compliance_report['summary']['not_tested'] += 1
        
        return compliance_report
    
    def _check_requirement_compliance(self, requirement: PerformanceRequirement) -> Dict:
        """Check if a specific requirement is met by benchmark results."""
        # Map requirement types to benchmark patterns
        benchmark_patterns = {
            'response_time': ['readRange', 'latency'],
            'throughput': ['throughput', 'ops'],
            'memory_usage': ['memory', 'allocation'],
            'cache_hit_ratio': ['cache', 'hit'],
            'concurrent_ops': ['concurrent', 'thread']
        }
        
        patterns = benchmark_patterns.get(requirement.metric, [requirement.metric])
        relevant_results = []
        
        for result in self.results:
            if any(pattern.lower() in result.benchmark.lower() for pattern in patterns):
                relevant_results.append(result)
        
        if not relevant_results:
            return {
                'requirement': requirement.name,
                'target': f"{requirement.target} {requirement.unit}",
                'status': 'NOT_TESTED',
                'message': 'No relevant benchmark results found',
                'results': []
            }
        
        # Check compliance based on requirement type
        compliance_results = []
        overall_pass = True
        
        for result in relevant_results:
            meets_requirement = self._evaluate_result(requirement, result)
            compliance_results.append({
                'benchmark': result.benchmark,
                'value': f"{result.score:.2f} {result.unit}",
                'passes': meets_requirement,
                'margin': self._calculate_margin(requirement, result)
            })
            if not meets_requirement:
                overall_pass = False
        
        return {
            'requirement': requirement.name,
            'target': f"{requirement.target} {requirement.unit}",
            'status': 'PASS' if overall_pass else 'FAIL',
            'message': 'All benchmarks meet requirement' if overall_pass else 'Some benchmarks fail requirement',
            'results': compliance_results
        }
    
    def _evaluate_result(self, requirement: PerformanceRequirement, result: BenchmarkResult) -> bool:
        """Evaluate if a benchmark result meets the requirement."""
        # Convert units if necessary
        result_value = self._normalize_value(result.score, result.unit, requirement.unit)
        
        # Different requirements have different comparison logic
        if requirement.metric in ['response_time', 'memory_usage']:
            # Lower is better
            return result_value <= requirement.target
        elif requirement.metric in ['throughput', 'cache_hit_ratio', 'concurrent_ops']:
            # Higher is better
            return result_value >= requirement.target
        else:
            # Default: exact match or higher
            return result_value >= requirement.target
    
    def _normalize_value(self, value: float, from_unit: str, to_unit: str) -> float:
        """Normalize values between different units."""
        # Simple unit conversion for common cases
        conversions = {
            ('ns', 'ms'): lambda x: x / 1_000_000,
            ('μs', 'ms'): lambda x: x / 1_000,
            ('ms', 'ms'): lambda x: x,
            ('ops/s', 'ops/sec'): lambda x: x,
            ('MB', 'MB'): lambda x: x,
            ('B', 'MB'): lambda x: x / (1024 * 1024),
        }
        
        converter = conversions.get((from_unit, to_unit))
        return converter(value) if converter else value
    
    def _calculate_margin(self, requirement: PerformanceRequirement, result: BenchmarkResult) -> str:
        """Calculate the margin by which a result passes or fails."""
        result_value = self._normalize_value(result.score, result.unit, requirement.unit)
        
        if requirement.metric in ['response_time', 'memory_usage']:
            # Lower is better
            margin = ((requirement.target - result_value) / requirement.target) * 100
        else:
            # Higher is better
            margin = ((result_value - requirement.target) / requirement.target) * 100
        
        return f"{margin:+.1f}%"
    
    def generate_report(self, compliance_data: Dict, output_file: Path) -> None:
        """Generate a markdown report of the compliance analysis."""
        report = []
        report.append("# Performance Requirement Compliance Report")
        report.append("")
        report.append(f"**Overall Status**: {compliance_data['overall_status']}")
        report.append("")
        
        # Summary
        summary = compliance_data['summary']
        report.append("## Summary")
        report.append("")
        report.append(f"- **Total Requirements**: {summary['total_requirements']}")
        report.append(f"- **Passed**: {summary['passed']} ✅")
        report.append(f"- **Failed**: {summary['failed']} ❌")
        report.append(f"- **Not Tested**: {summary['not_tested']} ⚠️")
        report.append("")
        
        # Detailed results
        report.append("## Detailed Results")
        report.append("")
        
        for req in compliance_data['requirements']:
            status_emoji = {'PASS': '✅', 'FAIL': '❌', 'NOT_TESTED': '⚠️'}[req['status']]
            report.append(f"### {req['requirement']} {status_emoji}")
            report.append("")
            report.append(f"**Target**: {req['target']}")
            report.append(f"**Status**: {req['status']}")
            report.append(f"**Message**: {req['message']}")
            report.append("")
            
            if req['results']:
                report.append("**Benchmark Results**:")
                report.append("")
                report.append("| Benchmark | Value | Passes | Margin |")
                report.append("|-----------|-------|--------|--------|")
                
                for result in req['results']:
                    passes_icon = "✅" if result['passes'] else "❌"
                    report.append(f"| {result['benchmark']} | {result['value']} | {passes_icon} | {result['margin']} |")
                
                report.append("")
        
        # Recommendations
        failed_reqs = [req for req in compliance_data['requirements'] if req['status'] == 'FAIL']
        if failed_reqs:
            report.append("## Recommendations")
            report.append("")
            report.append("The following requirements are not being met:")
            report.append("")
            for req in failed_reqs:
                report.append(f"- **{req['requirement']}**: {req['message']}")
            report.append("")
            report.append("Consider implementing performance optimizations or reviewing requirement targets.")
        
        output_file.write_text('\n'.join(report))

def main():
    parser = argparse.ArgumentParser(description='Analyze benchmark performance against requirements')
    parser.add_argument('--results', required=True, help='JMH benchmark results JSON file')
    parser.add_argument('--requirements', required=True, help='Requirements markdown file')
    parser.add_argument('--output', required=True, help='Output report file')
    
    args = parser.parse_args()
    
    results_file = Path(args.results)
    requirements_file = Path(args.requirements)
    output_file = Path(args.output)
    
    if not results_file.exists():
        print(f"Error: Results file {results_file} not found")
        sys.exit(1)
    
    if not requirements_file.exists():
        print(f"Error: Requirements file {requirements_file} not found")
        sys.exit(1)
    
    analyzer = PerformanceAnalyzer()
    analyzer.parse_requirements(requirements_file)
    analyzer.parse_benchmark_results(results_file)
    
    compliance_data = analyzer.analyze_compliance()
    analyzer.generate_report(compliance_data, output_file)
    
    print(f"Performance analysis complete. Report saved to {output_file}")
    
    # Exit with error code if any critical requirements fail
    if compliance_data['overall_status'] == 'FAIL':
        print("ERROR: Critical performance requirements not met!")
        sys.exit(1)

if __name__ == '__main__':
    main()