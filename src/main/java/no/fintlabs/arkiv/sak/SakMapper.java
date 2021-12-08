package no.fintlabs.arkiv.sak;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.fint.model.arkiv.kodeverk.Saksstatus;
import no.fint.model.resource.FintLinks;
import no.fint.model.resource.administrasjon.personal.PersonalressursResource;
import no.fint.model.resource.arkiv.kodeverk.SaksstatusResource;
import no.fint.model.resource.arkiv.noark.ArkivressursResource;
import no.fint.model.resource.arkiv.noark.SakResource;
import no.fint.model.resource.felles.PersonResource;
import no.fintlabs.arkiv.kodeverk.consumers.ArkivressursResourceEntityConsumer;
import no.fintlabs.arkiv.kodeverk.consumers.PersonResourceEntityConsumer;
import no.fintlabs.arkiv.kodeverk.consumers.PersonalressursResourceEntityConsumer;
import no.fintlabs.arkiv.kodeverk.consumers.SaksstatusConsumer;
import no.fintlabs.arkiv.sak.model.SakDTO;
import no.fintlabs.arkiv.sak.model.SaksansvarligDto;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SakMapper {

    private final SaksstatusConsumer saksstatusConsumer;
    private final ArkivressursResourceEntityConsumer arkivressursResourceEntityConsumer;
    private final PersonalressursResourceEntityConsumer personalressursResourceEntityConsumer;
    private final PersonResourceEntityConsumer personResourceEntityConsumer;
    private final ObjectMapper objectMapper;

    private SakMapper(
            ObjectMapper objectMapper,
            SaksstatusConsumer saksstatusConsumer,
            ArkivressursResourceEntityConsumer arkivressursResourceEntityConsumer,
            PersonalressursResourceEntityConsumer personalressursResourceEntityConsumer,
            PersonResourceEntityConsumer personResourceEntityConsumer
    ) {
        this.objectMapper = objectMapper;
        this.saksstatusConsumer = saksstatusConsumer;
        this.arkivressursResourceEntityConsumer = arkivressursResourceEntityConsumer;
        this.personalressursResourceEntityConsumer = personalressursResourceEntityConsumer;
        this.personResourceEntityConsumer = personResourceEntityConsumer;
    }

    public SakDTO toSakDTO(SakResource sakResource) {
        SakDTO sakDTO = new SakDTO();
        sakDTO.setCaseNumber(sakResource.getMappeId().getIdentifikatorverdi());
        sakDTO.setDate(sakResource.getSaksdato());
        sakDTO.setTitle(sakResource.getTittel());
        sakDTO.setStatus(this.getSaksstatus(sakResource));
        sakDTO.setCaseworker(this.getSaksansvarlig(sakResource));
        return sakDTO;
    }

    private Saksstatus getSaksstatus(SakResource sakResource) {
        if (sakResource.getSaksstatus().isEmpty()) {
            throw new IllegalStateException("No saksstatus links for resource=" + sakResource);
        }
        String saksstatusHref = sakResource.getSaksstatus().get(0).getHref();
        Optional<SaksstatusResource> saksstatusResource = this.saksstatusConsumer.getResourceCache().get(saksstatusHref);

        return saksstatusResource
                .map(resource -> this.objectMapper.convertValue(resource, Saksstatus.class))
                .orElseThrow(() -> this.createNoCachedResourceException("saksstatus", saksstatusHref));
    }

    private SaksansvarligDto getSaksansvarlig(SakResource sakResource) {
        ArkivressursResource saksansvarligArkivressursResource = getSaksansvarligArkivressursResource(sakResource);
        PersonResource personResource = this.getPersonResource(saksansvarligArkivressursResource);

        SaksansvarligDto saksansvarligDto = new SaksansvarligDto();
        saksansvarligDto.setKildesystemId(saksansvarligArkivressursResource.getKildesystemId() != null
                ? saksansvarligArkivressursResource.getKildesystemId().getIdentifikatorverdi()
                : null
        );
        saksansvarligDto.setSystemId(saksansvarligArkivressursResource.getSystemId() != null
                ? saksansvarligArkivressursResource.getSystemId().getIdentifikatorverdi()
                : null
        );
        saksansvarligDto.setPersonNavn(personResource.getNavn().toString());
        return saksansvarligDto;
    }

    private ArkivressursResource getSaksansvarligArkivressursResource(SakResource sakResource) {
        if (sakResource.getSaksansvarlig().isEmpty()) {
            this.throwNoLinkException(sakResource, "saksansvarlig");
        }
        String saksansvarligHref = sakResource.getSaksansvarlig().get(0).getHref();
        // TODO: 08/12/2021 Gets a system id link with value=62, but the cache only contains links with Strings of names
        return this.arkivressursResourceEntityConsumer.getResourceCache().get(saksansvarligHref)
                .orElseThrow(() -> this.createNoCachedResourceException("arkivressurs", saksansvarligHref));
    }

    private PersonResource getPersonResource(ArkivressursResource arkivressursResource) {
        if (arkivressursResource.getPersonalressurs().isEmpty()) {
            this.throwNoLinkException(arkivressursResource, "personalressurs");
        }
        String personalRessursHref = arkivressursResource.getPersonalressurs().get(0).getHref();
        PersonalressursResource personalressursResource = this.personalressursResourceEntityConsumer.getResourceCache().get(personalRessursHref)
                .orElseThrow(() -> this.createNoCachedResourceException("personalressurs", personalRessursHref));

        return this.getPersonResource(personalressursResource);
    }

    private PersonResource getPersonResource(PersonalressursResource personalressursResource) {
        if (personalressursResource.getPerson().isEmpty()) {
            this.throwNoLinkException(personalressursResource, "person");
        }
        String personHref = personalressursResource.getPerson().get(0).getHref();
        return this.personResourceEntityConsumer.getResourceCache().get(personHref)
                .orElseThrow(() -> this.createNoCachedResourceException("person", personHref));
    }

    private void throwNoLinkException(FintLinks resource, String linkedResourceName) {
        throw new IllegalStateException(String.format("No %s links for resource=%s", linkedResourceName, resource));
    }

    private IllegalStateException createNoCachedResourceException(String cachedResourceName, String cachedResourceHref) {
        return new IllegalStateException(
                String.format("No cached %s resource with self link=%s", cachedResourceName, cachedResourceHref)
        );
    }

}
