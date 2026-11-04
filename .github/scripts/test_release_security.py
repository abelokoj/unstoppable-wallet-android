"""Release trust-boundary checks. No credentials or external calls."""
import pathlib,re,unittest,yaml

ROOT=pathlib.Path(__file__).resolve().parents[2]
WORKFLOW=ROOT/'.github/workflows/release.yml'

class ReleaseSecurityTest(unittest.TestCase):
    def setUp(self):self.flow=yaml.safe_load(WORKFLOW.read_text())
    def test_actions_are_immutable(self):
        for job in self.flow['jobs'].values():
            for step in job.get('steps',[]):
                if 'uses' in step:self.assertRegex(step['uses'],r'^[\w./-]+@[0-9a-f]{40}$')
    def test_build_sign_and_publish_have_separate_permissions_and_runners(self):
        jobs=self.flow['jobs'];self.assertTrue({'build','sign','publish'}<=jobs.keys())
        self.assertEqual(jobs['build']['permissions'],{'contents':'read'})
        self.assertEqual(jobs['sign']['permissions'],{'contents':'read'})
        self.assertEqual(jobs['publish']['permissions'],{'contents':'write'})
        self.assertEqual(jobs['sign']['needs'],'build');self.assertEqual(jobs['publish']['needs'],['build','sign'])
    def test_production_secrets_exist_only_in_the_signing_step(self):
        secret_steps=[]
        for name,job in self.flow['jobs'].items():
            self.assertNotIn('secrets.',str(job.get('env',{})))
            for step in job.get('steps',[]):
                if 'secrets.' in str(step):secret_steps.append((name,step))
        self.assertEqual(len(secret_steps),1)
        name,step=secret_steps[0];self.assertEqual(name,'sign')
        self.assertIn('apksigner',step['run']);self.assertIn('trap ',step['run'])
        self.assertNotIn('gradlew',step['run']);self.assertNotIn('keystore.properties',step['run'])
        self.assertNotIn('secrets.',str(self.flow['jobs']['build']))
        self.assertNotIn('secrets.',str(self.flow['jobs']['publish']))
    def test_signing_job_does_not_execute_checkout_or_gradle(self):
        for step in self.flow['jobs'].get('sign',{}).get('steps',[]):
            self.assertNotIn('actions/checkout',step.get('uses',''))
            self.assertNotIn('gradlew',step.get('run',''))
    def test_no_raw_ref_interpolation_in_shell(self):
        for job in self.flow['jobs'].values():
            for step in job.get('steps',[]):self.assertNotIn('${{ github.ref_name }}',step.get('run',''))

if __name__=='__main__':unittest.main()
