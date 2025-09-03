#!/usr/bin/env python3
"""
Simple plotting utility for experiment data.

Usage:
    python plot_experiment_data.py <polling_data.json>
    python plot_experiment_data.py test_reports/polling_data_20250803_145030.json
"""

import json
import sys
import os

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.ticker import MaxNLocator

def load_polling_data(filename):
    """Load polling data from JSON file."""
    with open(filename, 'r') as f:
        return json.load(f)

def plot_experiment_data(data, save_path_base=None):
    if not data:
        print("No data to plot")
        return
    
    try:
        timestamps = [point['elapsed_time_seconds'] for point in data]
        
        # Plot 1: Submissions vs Results
        fig1, ax1 = plt.subplots(1, 1, figsize=(12, 8))
        submissions = [point['submissions_count'] for point in data]
        results = [point['results_count'] for point in data]
        
        ax1.plot(timestamps, submissions, 'b-', label='Submissions', marker='o', markersize=4)
        ax1.plot(timestamps, results, 'g-', label='Results', marker='s', markersize=4)
        ax1.set_xlabel('Time [s]', fontsize=12)
        ax1.set_ylabel('Count', fontsize=12)
        ax1.set_title('Student Submissions and Results', fontsize=14)
        ax1.legend(fontsize=12, bbox_to_anchor=(1.05, 1), loc='upper left')
        ax1.grid(True, alpha=0.3)
        
        # Limit number of x-axis ticks to prevent overlap
        ax1.xaxis.set_major_locator(MaxNLocator(nbins=6))
        
        if save_path_base:
            plot1_path = f"{save_path_base}_submissions_results.pdf"
            plt.savefig(plot1_path, dpi=300, bbox_inches='tight')
            print(f"Submissions and Results plot saved to {plot1_path}")
        else:
            plt.show()
        plt.close()
        
        # Plot 2: Job Queue Status
        fig2, ax2 = plt.subplots(1, 1, figsize=(12, 8))
        running_jobs = [point['running_jobs_count'] for point in data]
        queued_jobs = [point['queued_jobs_count'] for point in data]
        
        ax2.plot(timestamps, running_jobs, 'r-', label='Running Jobs', marker='o', markersize=4)
        ax2.plot(timestamps, queued_jobs, 'orange', label='Queued Jobs', marker='s', markersize=4)
        ax2.set_xlabel('Time [s]', fontsize=12)
        ax2.set_ylabel('# Build Jobs', fontsize=12)
        ax2.set_title('Queued and Running Build Jobs', fontsize=14)
        ax2.legend(fontsize=12, bbox_to_anchor=(1.05, 1), loc='upper left')
        ax2.grid(True, alpha=0.3)
        
        # Limit number of x-axis ticks to prevent overlap
        ax2.xaxis.set_major_locator(MaxNLocator(nbins=6))
        
        if save_path_base:
            plot2_path = f"{save_path_base}_job_queue.pdf"
            plt.savefig(plot2_path, dpi=300, bbox_inches='tight')
            print(f"Job Queue plot saved to {plot2_path}")
        else:
            plt.show()
        plt.close()
        
        # Plot 3: Individual Build Agent Load
        fig3, ax3 = plt.subplots(1, 1, figsize=(12, 8))
        
        # Get all unique agent names
        agent_names = set()
        for point in data:
            for agent in point.get('build_agents', []):
                agent_names.add(agent['name'])
        
        if agent_names:
            colors = plt.cm.tab10(range(len(agent_names)))
            
            for i, agent_name in enumerate(sorted(agent_names)):
                agent_jobs = []
                for point in data:
                    agent_info = next((a for a in point.get('build_agents', []) if a['name'] == agent_name), None)
                    jobs = agent_info['numberOfCurrentBuildJobs'] if agent_info else 0
                    agent_jobs.append(jobs)
                
                ax3.plot(timestamps, agent_jobs, color=colors[i], label=agent_name, 
                        marker='o', markersize=3, linewidth=2)
        
        ax3.set_xlabel('Time [s]', fontsize=12)
        ax3.set_ylabel('# Build Jobs', fontsize=12)
        ax3.set_title('Processed Jobs by Build Agent', fontsize=14)
        ax3.legend(fontsize=10, bbox_to_anchor=(1.05, 1), loc='upper left')
        ax3.grid(True, alpha=0.3)
        
        # Limit number of x-axis ticks to prevent overlap
        ax3.xaxis.set_major_locator(MaxNLocator(nbins=6))
        
        if save_path_base:
            plot3_path = f"{save_path_base}_build_agents.pdf"
            plt.savefig(plot3_path, dpi=300, bbox_inches='tight')
            print(f"Build Agent Load plot saved to {plot3_path}")
        else:
            plt.show()
        plt.close()
            
    except Exception as e:
        print(f"Error creating plots: {e}")
        import traceback
        traceback.print_exc()

def plot_combined_experiment_data(data, save_path_base=None):
    """Generate a combined plot with all three charts side by side in one figure."""
    if not data:
        print("No data to plot")
        return
    
    try:
        timestamps = [point['elapsed_time_seconds'] for point in data]
        
        # Create a figure with 3 subplots side by side
        fig, (ax1, ax2, ax3) = plt.subplots(1, 3, figsize=(18, 6))
        #fig.suptitle('Build System Monitoring - Complete Overview', fontsize=16)
        
        # Plot 1: Submissions vs Results
        submissions = [point['submissions_count'] for point in data]
        results = [point['results_count'] for point in data]
        
        ax1.plot(timestamps, submissions, 'b-', label='Submissions', marker='o', markersize=3)
        ax1.plot(timestamps, results, 'g-', label='Results', marker='s', markersize=3)
        ax1.set_xlabel('Time [s]', fontsize=10)
        ax1.set_ylabel('Count', fontsize=10)
        ax1.set_title('Student Submissions and Results', fontsize=12)
        ax1.legend(fontsize=9, loc='upper left')
        ax1.grid(True, alpha=0.3)
        
        # Limit number of x-axis ticks for combined view
        ax1.xaxis.set_major_locator(MaxNLocator(nbins=4))
        
        # Plot 2: Job Queue Status
        running_jobs = [point['running_jobs_count'] for point in data]
        queued_jobs = [point['queued_jobs_count'] for point in data]
        
        ax2.plot(timestamps, running_jobs, 'r-', label='Running Jobs', marker='o', markersize=3)
        ax2.plot(timestamps, queued_jobs, 'orange', label='Queued Jobs', marker='s', markersize=3)
        ax2.set_xlabel('Time [s]', fontsize=10)
        ax2.set_ylabel('# Build Jobs', fontsize=10)
        ax2.set_title('Queued and Running Build Jobs', fontsize=12)
        ax2.legend(fontsize=9, loc='upper left')
        ax2.grid(True, alpha=0.3)
        
        # Limit number of x-axis ticks for combined view
        ax2.xaxis.set_major_locator(MaxNLocator(nbins=4))
        
        # Plot 3: Individual Build Agent Load
        agent_names = set()
        for point in data:
            for agent in point.get('build_agents', []):
                agent_names.add(agent['name'])
        
        if agent_names:
            colors = plt.cm.tab10(range(len(agent_names)))
            
            for i, agent_name in enumerate(sorted(agent_names)):
                agent_jobs = []
                for point in data:
                    agent_info = next((a for a in point.get('build_agents', []) if a['name'] == agent_name), None)
                    jobs = agent_info['numberOfCurrentBuildJobs'] if agent_info else 0
                    agent_jobs.append(jobs)
                
                ax3.plot(timestamps, agent_jobs, color=colors[i], label=agent_name, 
                        marker='o', markersize=2, linewidth=1.5)
        
        ax3.set_xlabel('Time [s]', fontsize=10)
        ax3.set_ylabel('# Build Jobs', fontsize=10)
        ax3.set_title('Processed Jobs by Build Agent', fontsize=12)
        ax3.legend(fontsize=8, loc='upper left')
        ax3.grid(True, alpha=0.3)
        
        # Limit number of x-axis ticks for combined view
        ax3.xaxis.set_major_locator(MaxNLocator(nbins=4))
        
        plt.tight_layout()
        
        if save_path_base:
            combined_path = f"{save_path_base}_combined.pdf"
            plt.savefig(combined_path, dpi=300, bbox_inches='tight')
            print(f"Combined overview plot saved to {combined_path}")
        else:
            plt.show()
        plt.close()
            
    except Exception as e:
        print(f"Error creating combined plot: {e}")
        import traceback
        traceback.print_exc()

def main():
    if len(sys.argv) != 2:
        print("Usage: python plot_experiment_data.py <polling_data.json>")
        sys.exit(1)
    
    filename = sys.argv[1]
    
    if not os.path.exists(filename):
        print(f"File not found: {filename}")
        sys.exit(1)
    
    try:
        data = load_polling_data(filename)
        
        base_name = os.path.splitext(filename)[0]
        
        # Generate individual plots
        plot_experiment_data(data, base_name)
        
        # Generate combined plot
        plot_combined_experiment_data(data, base_name)
        
        # Print summary statistics
        print(f"\n=== Experiment Summary ===")
        print(f"Duration: {data[-1]['elapsed_time_seconds']:.1f} seconds")
        print(f"Data points collected: {len(data)}")
        print(f"Final submissions: {data[-1]['submissions_count']}")
        print(f"Final results: {data[-1]['results_count']}")
        print(f"Max running jobs: {max(point['running_jobs_count'] for point in data)}")
        print(f"Max queued jobs: {max(point['queued_jobs_count'] for point in data)}")
        
    except Exception as e:
        print(f"Error processing data: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
