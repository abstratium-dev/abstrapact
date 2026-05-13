import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { Config, Demo, ModelService } from './model.service';

@Injectable({
  providedIn: 'root',
})
export class Controller {

  private modelService = inject(ModelService);
  private http = inject(HttpClient);

  loadDemos() {
    this.modelService.setDemosLoading(true);
    this.modelService.setDemosError(null);
    
    this.http.get<Demo[]>('/api/demo').subscribe({
      next: (demos) => {
        this.modelService.setDemos(demos);
        this.modelService.setDemosLoading(false);
      },
      error: (err) => {
        console.error('Error loading demos:', err);
        this.modelService.setDemos([]);
        this.modelService.setDemosError('Failed to load demos');
        this.modelService.setDemosLoading(false);
      }
    });
  }

  async createDemo(): Promise<Demo> {
    try {
      const response = await firstValueFrom(
        this.http.post<Demo>('/api/demo', {})
      );
      // Reload demos list after successful creation
      this.loadDemos();
      return response;
    } catch (error) {
      console.error('Error creating demo:', error);
      throw error;
    }
  }

  async updateDemo(demo: Demo): Promise<Demo> {
    try {
      const response = await firstValueFrom(
        this.http.put<Demo>('/api/demo', demo)
      );
      // Reload demos list after successful update
      this.loadDemos();
      return response;
    } catch (error) {
      console.error('Error updating demo:', error);
      throw error;
    }
  }

  async deleteDemo(id: string): Promise<void> {
    try {
      await firstValueFrom(
        this.http.delete<void>(`/api/demo/${id}`)
      );
      // Reload demos list after successful deletion
      this.loadDemos();
    } catch (error) {
      console.error('Error deleting demo:', error);
      throw error;
    }
  }

  async triggerError(): Promise<void> {
    try {
      await firstValueFrom(
        this.http.get<void>('/api/demo/error')
      );
    } catch (error) {
      console.error('Error response:', error);
      throw error;
    }
  }

  async loadConfig(): Promise<Config> {
    try {
      const config = await firstValueFrom(
        this.http.get<Config>('/public/config')
      );
      this.modelService.setConfig(config);
      return config;
    } catch (error) {
      console.error('Error loading config:', error);
      throw error;
    }
  }
}
