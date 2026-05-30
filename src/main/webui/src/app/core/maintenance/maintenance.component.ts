import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { firstValueFrom } from 'rxjs';

@Component({
    selector: 'app-maintenance',
    imports: [CommonModule],
    templateUrl: './maintenance.component.html',
    styleUrl: './maintenance.component.scss'
})
export class MaintenanceComponent implements OnInit {
    private http = inject(HttpClient);

    maintenanceMessage = signal<string>('');
    hasMaintenanceScheduled = signal<boolean>(false);

    ngOnInit(): void {
        this.fetchMaintenanceToggle();
    }

    private async fetchMaintenanceToggle(): Promise<void> {
        try {
            const response = await firstValueFrom(
                this.http.get<{ [key: string]: string }>('/public/toggles')
            );
            const value = response['going-down-for-maintenance'] || '';
            // If value is 'off' or empty, no maintenance is scheduled
            const hasMaintenance = !!(value && value !== 'off');
            this.hasMaintenanceScheduled.set(hasMaintenance);
            this.maintenanceMessage.set(hasMaintenance ? value : '');
        } catch (err) {
            console.error('Failed to fetch maintenance toggle:', err);
            this.hasMaintenanceScheduled.set(false);
            this.maintenanceMessage.set('');
        }
    }
}
