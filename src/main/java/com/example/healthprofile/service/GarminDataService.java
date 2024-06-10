package com.example.healthprofile.service;

import com.example.healthprofile.entity.GarminData;
import com.example.healthprofile.entity.User;
import com.example.healthprofile.repository.GarminDataRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class GarminDataService {

    private final GarminDataRepository garminDataRepository;


    @Autowired
    public GarminDataService(GarminDataRepository garminDataRepository) {
        this.garminDataRepository = garminDataRepository;
    }

    public List<GarminData> getHeartRateDataForCurrentUserOnDate(Long userId, LocalDate date) {
        return garminDataRepository.findHeartRateDataForCurrentUserOnDate(userId, date);
    }


}
